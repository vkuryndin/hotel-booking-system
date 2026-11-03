import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { ArrowLeft, ArrowRight, BedDouble, Check, CheckCircle2, ChevronLeft, ChevronRight, Hotel, ImagePlus, Info, Pencil, Plus, RefreshCcw, Trash2, Upload, X } from 'lucide-react'
import { api } from '../api/client.js'
import { RoomPhotoImage, RoomPreviewSlider } from '../components/RoomPhotos.jsx'
import { RoomCreationWizard } from '../components/RoomCreationWizard.jsx'
import { Alert, Button, Empty, ImageBlock, HotelNoPhotos, PageTitle, Spinner, Pagination } from '../components/Shared.jsx'
import { arrayOf, formatMoney, normalRoom } from '../utils/format.js'
import { HOTEL_AMENITIES, AMENITY_LABEL } from '../constants/amenities.js'

const BLANK_PRESENTATION = {
  description: '', rating: '', amenities: [], checkInTime: '14:00', checkOutTime: '12:00',
}

function Field({ label, children }) {
  return <label className="field">{label}{children}</label>
}

function AmenitiesEditor({ value, onChange, items = HOTEL_AMENITIES, label = "Удобства отеля" }) {
  return <div className="amenity-editor" aria-label={label}>
    {items.map(item => {
      const selected = value.includes(item)
      return <label className={`amenity-choice ${selected ? 'is-selected' : ''}`} key={item}>
        <input
          type="checkbox"
          checked={selected}
          onChange={event => onChange(event.target.checked
            ? [...value, item]
            : value.filter(valueItem => valueItem !== item))}
        />
        <span>{AMENITY_LABEL[item]}</span>
      </label>
    })}
  </div>
}

function HotelPhotoEditor({ hotelId, hotelName, onPhotosChanged, onBusyChange }) {
  const [photos, setPhotos] = useState([])
  const [visibleCount, setVisibleCount] = useState(12)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  async function reload() {
    if (!hotelId) return
    const result = await api.photos(hotelId)
    setPhotos(arrayOf(result).sort((a,b) => Number(b.primary)-Number(a.primary) || Number(a.sortOrder??0)-Number(b.sortOrder??0)))
  }

  useEffect(() => {
    let active = true
    api.photos(hotelId).then(result => {
      if (active) setPhotos(arrayOf(result).sort((a,b) => Number(b.primary)-Number(a.primary) || Number(a.sortOrder??0)-Number(b.sortOrder??0)))
    }).catch(err => {
      if (active) setError(err.message)
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [hotelId])

  async function upload(event) {
    const files = Array.from(event.target.files || [])
    if (!files.length) return
    // The backend accepts JPEG, PNG and WebP, up to 5 MiB per file.
    if (files.some(file => !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024)) {
      setError('Разрешены только JPEG, PNG и WebP размером до 5 МБ каждый.')
      event.target.value = ''
      return
    }
    if (photos.length + files.length > 10) {
      setError(`Не более 10 фотографий на отель. Доступно мест: ${Math.max(0,10-photos.length)}.`)
      event.target.value=''
      return
    }
    setBusy(true)
    onBusyChange?.(true)
    setError('')
    setSuccess('')
    let uploaded=0
    try {
      for (const file of files) {await api.photoUpload(hotelId, file);uploaded++}
      await reload()
      onPhotosChanged?.()
      setSuccess(files.length === 1 ? 'Фотография загружена.' : `Загружено фотографий: ${files.length}.`)
    } catch (err) {
      setError(`${err.message} Загружено из выбранных: ${uploaded} из ${files.length}.`)
      try { await reload() } catch { /* Keep existing photos. */ }
    } finally {
      setBusy(false)
      onBusyChange?.(false)
      event.target.value = ''
    }
  }

  async function updatePhoto(action, successMessage) {
    setBusy(true)
    onBusyChange?.(true)
    setError('')
    setSuccess('')
    try {
      await action()
      await reload()
      onPhotosChanged?.()
      setSuccess(successMessage)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
      onBusyChange?.(false)
    }
  }

  return <section className="admin-inline-photos" aria-label="Фотографии отеля">
    <div className="admin-inline-photos-heading">
      <div>
        <h3><ImagePlus size={18} /> Фотографии отеля</h3>
        <p>Загружено фотографий: {photos.length}. Выберите главное изображение или добавьте новые.</p>
      </div>
      <div className="admin-photos-header-actions">
        <Button type="button" variant="ghost" size="small" disabled={busy} onClick={() => {
          setError('')
          reload().catch(err => setError(err.message))
        }}><RefreshCcw size={15} /> Обновить</Button>
      </div>
    </div>
    <Alert error={error} success={success} />
    {loading ? <Spinner label="Загружаем фотографии..." />
      : <div className="admin-inline-photos-grid">
        {photos.slice(0, visibleCount).map((photo, index) => <div className="admin-inline-photo" key={photo.id}>
          <ImageBlock hotelId={hotelId} photoId={photo.id} alt={`${hotelName}, фотография ${index + 1}`} fallback={index} />
          <div className="admin-inline-photo-footer">
            {photo.primary ? <span className="photo-main-label"><CheckCircle2 size={14} /> Главное фото</span>
              : <Button type="button" variant="ghost" size="small" disabled={busy}
                onClick={() => updatePhoto(() => api.photoPrimary(hotelId, photo.id), 'Главная фотография изменена.')}>Сделать главным</Button>}
            <Button type="button" size="small" variant="ghost" disabled={busy}
              aria-label={`Удалить фотографию ${index + 1}`}
              onClick={() => {
                if (window.confirm('Удалить эту фотографию?')) {
                  updatePhoto(() => api.photoDelete(hotelId, photo.id), 'Фотография удалена.')
                }
              }}><Trash2 size={16} /></Button>
          </div>
        </div>)}
        {photos.length<10&&<label className={`admin-photo-upload-tile ${busy ? 'is-disabled' : ''}`}>
          <Upload size={26} aria-hidden="true" />
          <b>{busy ? 'Загружаем...' : 'Добавить фото'}</b>
          <small>JPEG, PNG или WebP, до 5 МБ</small>
          <input type="file" accept="image/jpeg,image/png,image/webp" multiple disabled={busy}
            onChange={upload} aria-label="Загрузить фотографии отеля" />
        </label>}
      </div>}
    {!loading && photos.length > visibleCount && <div className="gallery-more-actions"><Button type="button" variant="outline" disabled={busy} onClick={() => setVisibleCount(n => n + 12)}>Показать ещё ({photos.length - visibleCount})</Button><span>Показано {Math.min(photos.length, visibleCount)} из {photos.length}</span></div>}
  </section>
}



const STRATEGIES = [
  { value: 'LEAST_BOOKED', label: 'Наименее загруженный номер', description: 'Распределяет бронирования между подходящими номерами.' },
  { value: 'MOST_EXPENSIVE', label: 'Приоритет более высокой категории', description: 'Предлагает более дорогие подходящие номера в первую очередь.' },
]
function readStrategy(data) {
  const value = typeof data === 'string' ? data : data?.strategy || data?.roomSelectionStrategy
  return STRATEGIES.some(entry => entry.value === value) ? value : 'LEAST_BOOKED'
}
const WIZARD_STEPS = ['Основное', 'Оформление', 'Фотографии', 'Номера', 'Проверка']

// Compact photo slider for the first step of the hotel wizard.
// Only the selected image and three full-size thumbnails mount, even with 50+ photos.
function HotelWizardPreviewSlider({ hotelId, photos, hotelName, onShowAll }) {
  const [position, setPosition] = useState(0)
  useEffect(() => { setPosition(0) }, [hotelId])
  const selected = Math.min(position, Math.max(photos.length - 1, 0))
  const current = photos[selected]
  const thumbWindowSize = 3
  const start = Math.max(0, Math.min(selected - 1, photos.length - thumbWindowSize))
  const visible = photos.slice(start, start + thumbWindowSize)
  const go = direction => setPosition(index => (Math.min(index, photos.length - 1) + direction + photos.length) % photos.length)

  if (!hotelId || !photos.length) return <div className="wizard-preview-art"><Hotel size={72}/></div>

  return <div className="hotel-wizard-preview-slider" aria-label={`Фотографии отеля ${hotelName}`}>
    <div className="room-wizard-slider-image hover-photo-zoom">
      <ImageBlock key={current.id} hotelId={hotelId} photoId={current.id} alt={`${hotelName}, фото ${selected + 1}`} />
      {photos.length > 1 && <>
        <button type="button" className="room-slider-prev" onClick={() => go(-1)} aria-label="Предыдущая фотография отеля"><ChevronLeft size={20}/></button>
        <button type="button" className="room-slider-next" onClick={() => go(1)} aria-label="Следующая фотография отеля"><ChevronRight size={20}/></button>
        <span className="room-slider-count" aria-live="polite">{selected + 1} / {photos.length}</span>
      </>}
    </div>
    {photos.length > 1 && <div className="hotel-wizard-preview-thumbnails" aria-label="Миниатюры фотографий отеля">
      {visible.map((photo, index) => <button
        type="button" key={photo.id}
        className={selected === start + index ? 'selected' : ''}
        onClick={() => setPosition(start + index)}
        aria-label={`Показать фотографию ${start + index + 1} из ${photos.length}`}
        aria-current={selected === start + index ? 'true' : undefined}
      ><ImageBlock hotelId={hotelId} photoId={photo.id} alt={`Миниатюра ${start + index + 1}`} /></button>)}
    </div>}
    <div className="hotel-wizard-preview-caption">
      <span>{photos.length} фото отеля</span>
      <button type="button" onClick={onShowAll} aria-label="Открыть все фотографии отеля на шаге 3">Все фотографии <ArrowRight size={14}/></button>
    </div>
  </div>
}

// Final review: all hotel photos are accessible, not just the primary image.
// Render at most 12 thumbnails at a time so large galleries remain responsive.
const REVIEW_PHOTO_PAGE_SIZE = 12
function HotelReviewGallery({ hotelId, hotelName, photos = [] }) {
  const ordered = [...photos].sort((a, b) => Number(b.primary) - Number(a.primary)
    || Number(a.sortOrder ?? 0) - Number(b.sortOrder ?? 0))
  const [selected, setSelected] = useState(0)
  const [page, setPage] = useState(0)
  const total = ordered.length
  const totalPages = Math.max(1, Math.ceil(total / REVIEW_PHOTO_PAGE_SIZE))
  const activeIndex = Math.min(selected, Math.max(total - 1, 0))
  const safePage = Math.min(page, totalPages - 1)
  const start = safePage * REVIEW_PHOTO_PAGE_SIZE
  const visiblePhotos = ordered.slice(start, start + REVIEW_PHOTO_PAGE_SIZE)

  useEffect(() => { setSelected(0); setPage(0) }, [hotelId])

  function selectPhoto(index) {
    if (!total) return
    const next = ((index % total) + total) % total
    setSelected(next)
    setPage(Math.floor(next / REVIEW_PHOTO_PAGE_SIZE))
  }

  if (!hotelId || !total) return <div className="wizard-review-gallery-empty"><HotelNoPhotos /></div>

  return <section className="wizard-review-hotel-gallery" aria-label={`Все фотографии отеля ${hotelName}`}>
    <div className="wizard-review-gallery-heading">
      <strong>Фотографии отеля</strong>
      <span>{total} фото</span>
    </div>
    <div className="wizard-review-gallery-hero hover-photo-zoom">
      <ImageBlock hotelId={hotelId} photoId={ordered[activeIndex].id} alt={`${hotelName}, фотография ${activeIndex + 1} из ${total}`} />
      {total > 1 && <>
        <button className="wizard-review-gallery-prev" type="button" onClick={() => selectPhoto(activeIndex - 1)} aria-label="Предыдущая фотография отеля"><ChevronLeft size={20} /></button>
        <button className="wizard-review-gallery-next" type="button" onClick={() => selectPhoto(activeIndex + 1)} aria-label="Следующая фотография отеля"><ChevronRight size={20} /></button>
        <span className="wizard-review-gallery-count" aria-live="polite">{activeIndex + 1} / {total}</span>
      </>}
    </div>
    <div className="wizard-review-gallery-thumbnails" aria-label="Миниатюры всех фотографий отеля">
      {visiblePhotos.map((photo, i) => {
        const photoIndex = start + i
        return <button type="button" key={photo.id} className={photoIndex === activeIndex ? 'is-active' : ''}
          onClick={() => selectPhoto(photoIndex)} aria-label={`Выбрать фотографию отеля ${photoIndex + 1} из ${total}`}
          aria-pressed={photoIndex === activeIndex}>
          <ImageBlock hotelId={hotelId} photoId={photo.id} alt={`Фотография ${photoIndex + 1}`} />
        </button>
      })}
    </div>
    <div className="wizard-review-gallery-pagination">
      <span>Показано {start + 1}–{Math.min(start + REVIEW_PHOTO_PAGE_SIZE, total)} из {total}</span>
      {totalPages > 1 && <div className="wizard-review-gallery-page-actions">
        <button type="button" disabled={safePage === 0} onClick={() => selectPhoto((safePage - 1) * REVIEW_PHOTO_PAGE_SIZE)} aria-label="Предыдущие 12 фотографий"><ChevronLeft size={17} /></button>
        <span>{safePage + 1} / {totalPages}</span>
        <button type="button" disabled={safePage >= totalPages - 1} onClick={() => selectPhoto((safePage + 1) * REVIEW_PHOTO_PAGE_SIZE)} aria-label="Следующие 12 фотографий"><ChevronRight size={17} /></button>
      </div>}
    </div>
  </section>
}

function HotelWizard({ hotelToEdit = null, onClose, onComplete }) {
  const editingMode = Boolean(hotelToEdit?.id)
  const [step, setStep] = useState(0)
  const [draft, setDraft] = useState({ name: '', address: '', roomSelectionStrategy: 'LEAST_BOOKED', ...BLANK_PRESENTATION })
  const [createdHotel, setCreatedHotel] = useState(hotelToEdit)
  const [originalIdentity, setOriginalIdentity] = useState({ name: hotelToEdit?.name || '', address: hotelToEdit?.address || '' })
  const [initializing, setInitializing] = useState(editingMode)
  const [loadFailure, setLoadFailure] = useState(false)
  const [rooms, setRooms] = useState([])
  const [roomPage,setRoomPage] = useState(0)
  const [roomPages,setRoomPages] = useState(1)
  const [roomTotal,setRoomTotal] = useState(0)
  const roomsRequestRef = useRef(0)
  const [roomWizard, setRoomWizard] = useState(null)
  const [photoCount, setPhotoCount] = useState(0)
  const [hotelPhotos, setHotelPhotos] = useState([])
  const [busy, setBusy] = useState(false)
  const [photoBusy, setPhotoBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const dialogRef = useRef(null)
  const firstFieldRef = useRef(null)

  useEffect(() => {
    const previousFocus = document.activeElement
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    firstFieldRef.current?.focus()
    return () => { document.body.style.overflow = previousOverflow; if(previousFocus?.isConnected)previousFocus.focus() }
  }, [])

  useEffect(() => {
    if (!hotelToEdit?.id) return
    let active = true
    async function initialize() {
      setInitializing(true)
      setLoadFailure(false)
      setError('')
      try {
        const id = hotelToEdit.id
        const results = await Promise.allSettled([api.hotel(id), api.roomSelectionStrategy(id), api.roomsPage({hotelId:id,page:0,size:20}), api.photos(id)])
        if (!active) return
        if (results[0].status !== 'fulfilled') throw results[0].reason
        const hotel = results[0].value
        const strategy = results[1].status === 'fulfilled' ? readStrategy(results[1].value) : 'LEAST_BOOKED'
        setCreatedHotel(hotel)
        setOriginalIdentity({ name: hotel.name || '', address: hotel.address || '' })
        setDraft({
          name: hotel.name || '', address: hotel.address || '',
          description: hotel.description || '', rating: hotel.rating ?? '',
          amenities: arrayOf(hotel.amenities),
          checkInTime: String(hotel.checkInTime || '14:00').slice(0,5),
          checkOutTime: String(hotel.checkOutTime || '12:00').slice(0,5),
          roomSelectionStrategy: strategy,
        })
        if (results[2].status === 'fulfilled') {setRooms(arrayOf(results[2].value).map(normalRoom));setRoomTotal(Number(results[2].value.totalElements||0));setRoomPages(Math.max(1,Number(results[2].value.totalPages||1)))}
        if (results[3].status === 'fulfilled') { const existingPhotos = arrayOf(results[3].value).sort((a,b)=>Number(b.primary)-Number(a.primary)||Number(a.sortOrder??0)-Number(b.sortOrder??0)); setHotelPhotos(existingPhotos); setPhotoCount(existingPhotos.length) }
        const incomplete = results.slice(1).filter(result => result.status === 'rejected')
        if (incomplete.length) setError(`Не всё удалось загрузить: ${incomplete.map(result => result.reason?.message || 'Ошибка API').join('; ')}. Повторная загрузка произойдёт при переходе к разделу.`)
      } catch (err) {
        if (active) { setLoadFailure(true); setError(`Не удалось загрузить отель: ${err.message || 'Ошибка API'}. Закройте мастер и повторите.`) }
      } finally { if (active) setInitializing(false) }
    }
    initialize()
    return () => { active = false }
  }, [hotelToEdit?.id])

  function closeWizard() {
    if (busy || photoBusy) return
    const hasDraft = draft.name.trim() || draft.address.trim() || draft.description.trim()
    if ((createdHotel || hasDraft) && !window.confirm(editingMode
      ? 'Закрыть редактирование отеля? Несохранённые данные текущего шага будут потеряны. Уже сохранённые изменения останутся.'
      : createdHotel ? 'Отель уже создан в каталоге. Закрыть мастер? Его можно будет дополнить позже в панели администратора.'
      : 'Отменить создание отеля? Несохранённые данные будут потеряны.')) return
    onClose()
  }

  useEffect(() => {
    function onKeyDown(event) {
      if (event.key === 'Escape' && !roomWizard) {
        event.preventDefault()
        closeWizard()
      }
      if (event.key === 'Tab' && !roomWizard && dialogRef.current) {
        const focusable = [...dialogRef.current.querySelectorAll('a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])')]
          .filter(el => el.getClientRects().length > 0)
        if (!focusable.length) return
        const first = focusable[0]
        const last = focusable[focusable.length - 1]
        if (event.shiftKey && document.activeElement === first) { last.focus(); event.preventDefault() }
        if (!event.shiftKey && document.activeElement === last) { first.focus(); event.preventDefault() }
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  })

  useEffect(() => {
    if (!createdHotel || editingMode) return
    function warnBeforeUnload(event) { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', warnBeforeUnload)
    return () => window.removeEventListener('beforeunload', warnBeforeUnload)
  }, [createdHotel, editingMode])

  function changeDraft(field, value) {
    setError('')
    setDraft(previous => ({ ...previous, [field]: value }))
  }

  async function loadRooms(hotelId, targetPage=roomPage) {
    const requestId=++roomsRequestRef.current
    const data=await api.roomsPage({hotelId,page:targetPage,size:20})
    if(requestId!==roomsRequestRef.current)return
    setRoomPage(targetPage)
    setRoomTotal(Number(data.totalElements||0))
    setRoomPages(Math.max(1,Number(data.totalPages||1)))
    setRooms(arrayOf(data).map(normalRoom))
  }

  async function loadPhotos(hotelId) {
    const result = arrayOf(await api.photos(hotelId))
    setPhotoCount(result.length)
    setHotelPhotos(result)
  }

  async function nextStep(event) {
    event?.preventDefault()
    if (busy || photoBusy || initializing || loadFailure) return
    setError('')
    setNotice('')
    if (step === 0) {
      if (!draft.name.trim() || !draft.address.trim()) {
        setError('Введите название и адрес отеля.')
        return
      }
      if (draft.name.trim().length > 150 || draft.address.trim().length > 255) {
        setError('Название отеля — до 150 символов, адрес — до 255.'); return
      }
      if (createdHotel?.id && (draft.name.trim() !== originalIdentity.name || draft.address.trim() !== originalIdentity.address)) {
        setBusy(true)
        try {
          const updated = await api.updateHotelIdentity(createdHotel.id, draft.name.trim(), draft.address.trim())
          setCreatedHotel(updated)
          setOriginalIdentity({ name: draft.name.trim(), address: draft.address.trim() })
          setNotice('Название и адрес отеля сохранены.')
        } catch (err) { setError(err.message); return }
        finally { setBusy(false) }
      }
      setStep(1)
      return
    }
    if (step === 1) {
      const rating = draft.rating === '' ? null : Number(draft.rating)
      if (rating !== null && (!Number.isFinite(rating) || rating < 0 || rating > 5)) {
        setError('Рейтинг должен быть от 0 до 5.')
        return
      }
      if (!/^\d{2}:\d{2}$/.test(draft.checkInTime) || !/^\d{2}:\d{2}$/.test(draft.checkOutTime)) {
        setError('Укажите время заезда и выезда.')
        return
      }
      setBusy(true)
      let hotel = createdHotel
      try {
        if (!hotel) {
          // A hotel is created only once; subsequent wizard navigation does not duplicate it.
          hotel = await api.createHotel({ name: draft.name.trim(), address: draft.address.trim() })
          if (!hotel?.id) throw new Error('Отель создан, но сервер не вернул ID. Проверьте каталог перед повтором.')
          setCreatedHotel(hotel)
        }
        await api.editHotel(hotel.id, {
          description: draft.description.trim(),
          ...(rating !== null ? { rating } : {}),
          amenities: draft.amenities,
          checkInTime: `${draft.checkInTime}:00`,
          checkOutTime: `${draft.checkOutTime}:00`,
        })
        await api.setRoomSelectionStrategy(hotel.id, draft.roomSelectionStrategy)
        setStep(2)
        setNotice(editingMode ? 'Изменения отеля сохранены. Проверьте фотографии.' : 'Отель создан. Теперь можно добавить фотографии.')
      } catch (err) {
        setError(`${err.message || 'Не удалось сохранить оформление.'}${!editingMode && hotel?.id ? ' Отель уже существует: повторное нажатие не создаст дубликат.' : ''}`)
      } finally { setBusy(false) }
      return
    }
    if (step === 2) {
      setBusy(true)
      try {
        await loadPhotos(createdHotel.id)
        await loadRooms(createdHotel.id)
        setStep(3)
      } catch (err) { setError(err.message) } finally { setBusy(false) }
      return
    }
    if (step === 3) {
      setBusy(true)
      try {
        await loadRooms(createdHotel.id)
        await loadPhotos(createdHotel.id)
        setStep(4)
      } catch (err) { setError(err.message) } finally { setBusy(false) }
      return
    }
    if (step === 4) onComplete({ ...createdHotel, name: draft.name.trim(), address: draft.address.trim() })
  }

  function backStep() {
    setError('')
    setNotice('')
    // The same wizard works for creation and editing. Identity is persisted by PUT when editing.
    if (step > 0) setStep(step - 1)
  }

  const ready = Boolean(createdHotel?.id)
  const previewId = createdHotel?.id
  const allAmenities = draft.amenities.map(a => AMENITY_LABEL[a] || a)

  return <><div className="hotel-wizard-overlay" role="presentation">
    <section className="hotel-wizard" role="dialog" aria-modal="true" aria-labelledby="hotel-wizard-title" ref={dialogRef}>
      <header className="hotel-wizard-header">
        <div>
          <h2 id="hotel-wizard-title">{editingMode ? `Редактирование: ${draft.name || hotelToEdit?.name}` : 'Создание нового отеля'}</h2>
          <p>{editingMode ? 'Изменяйте основные данные, оформление, фотографии и номера в одном мастере.' : 'Добавьте информацию об отеле, фотографии и номера за несколько шагов.'}</p>
        </div>
        <button className="wizard-close" type="button" aria-label="Закрыть мастер" onClick={closeWizard} disabled={busy || photoBusy}>
          <span>Закрыть</span><X size={20}/>
        </button>
      </header>
      <nav className="wizard-steps" aria-label={editingMode ? 'Этапы редактирования отеля' : 'Этапы создания отеля'}>
        {WIZARD_STEPS.map((item, index) => <div className={`wizard-step ${index === step ? 'is-active' : ''} ${index < step ? 'is-done' : ''}`} key={item} aria-current={index === step ? 'step' : undefined}>
          <span className="wizard-step-number">{index < step ? <Check size={18} strokeWidth={3}/> : index + 1}</span>
          <span className="wizard-step-label">{item}</span>
        </div>)}
      </nav>
      <div className="hotel-wizard-body" key={step}>
        {initializing && <Spinner label="Загружаем данные отеля, фотографии и номера..."/>}
        {loadFailure && <div className="wizard-warning"><Info size={19}/><div>Не удалось открыть редактирование. Закройте мастер и попробуйте ещё раз.</div></div>}
        {!initializing && !loadFailure && <>
        {step === 0 && <>
          <div className="wizard-section-head"><h3>Шаг 1. Основные данные</h3><p>Название и адрес будут отображаться в каталоге и на странице отеля.</p></div>
          <div className="wizard-split">
            <form id="wizard-main-form" className="wizard-form admin-form" onSubmit={nextStep}>
              <Field label="Название отеля *"><input ref={firstFieldRef} value={draft.name} onChange={e => changeDraft('name', e.target.value)} required maxLength={150} placeholder="Например, Grand Hotel Moscow"/></Field>
              <Field label="Адрес отеля *"><input value={draft.address} onChange={e => changeDraft('address', e.target.value)} required maxLength={255} placeholder="Город, страна, улица и номер дома"/></Field>
              <div className="wizard-tip"><Info size={19}/><div><b>Важно</b><p>{editingMode ? 'При переходе к следующему шагу изменения названия и адреса сохраняются в Hotel Service.' : 'Название и адрес будут отправлены в API после заполнения следующего шага.'}</p></div></div>
            </form>
            <aside className="wizard-preview">
              {editingMode && hotelPhotos.length ? <HotelWizardPreviewSlider
                hotelId={createdHotel?.id} photos={hotelPhotos} hotelName={draft.name}
                onShowAll={() => setStep(2)}
              /> : editingMode ? <div className="wizard-preview-art"><HotelNoPhotos/></div> : <div className="wizard-preview-art"><Hotel size={72}/></div>}
              <b>{draft.name || 'Название будущего отеля'}</b><p>{draft.address || 'Адрес появится здесь'}</p>
              {!editingMode && <span>Предпросмотр карточки отеля</span>}
            </aside>
          </div>
        </>}
        {step === 1 && <>
          <div className="wizard-section-head"><h3>Шаг 2. Оформление</h3><p>{editingMode ? 'Изменения оформления и стратегии будут сохранены перед открытием фотографий.' : 'Описание, удобства и правила заезда. При продолжении отель будет создан в базе.'}</p></div>
          <form id="wizard-main-form" className="wizard-form admin-form" onSubmit={nextStep}>
            <Field label="Описание отеля"><textarea rows={4} maxLength={3000} value={draft.description} onChange={e => changeDraft('description', e.target.value)} placeholder="Опишите отель и его преимущества" /></Field>
            <div className="form-row wizard-presentation-grid">
              <Field label="Рейтинг (0–5)"><input type="number" min="0" max="5" step="0.1" value={draft.rating} onChange={e => changeDraft('rating', e.target.value)} placeholder="Например, 4.5"/></Field>
              <Field label="Время заезда"><input type="time" required value={draft.checkInTime} onChange={e => changeDraft('checkInTime', e.target.value)}/></Field>
              <Field label="Время выезда"><input type="time" required value={draft.checkOutTime} onChange={e => changeDraft('checkOutTime', e.target.value)}/></Field>
            </div>
            <h4>Удобства отеля</h4>
            <AmenitiesEditor value={draft.amenities} onChange={value => changeDraft('amenities', value)}/>
            <h4 className="wizard-strategy-label">Стратегия автоматического подбора номеров</h4>
            <p className="muted">Применяется ко всем новым автоматическим бронированиям этого отеля. Пользователь не выбирает стратегию.</p>
            <div className="wizard-strategies">{STRATEGIES.map(item => <label className={`strategy-choice ${draft.roomSelectionStrategy === item.value ? 'is-selected' : ''}`} key={item.value}><input type="radio" name="wizard-room-strategy" checked={draft.roomSelectionStrategy === item.value} onChange={() => changeDraft('roomSelectionStrategy', item.value)}/><span><strong>{item.label}</strong><small>{item.description}</small></span></label>)}</div>
            <div className="wizard-tip"><Info size={19}/><div><b>Сохранение</b><p>{editingMode ? 'При нажатии «Сохранить и далее» обновятся оформление и стратегия существующего отеля.' : 'На следующем этапе отель будет создан и сохранён в каталоге. Если вы выйдете позже, запись останется в базе.'}</p></div></div>
          </form>
        </>}
        {step === 2 && <>
          <div className="wizard-section-head"><h3>Шаг 3. Фотографии</h3><p>{editingMode ? 'Здесь отображаются все фотографии отеля из backend, включая демоизображения. Можно выбрать главное фото, удалить или добавить новое.' : 'Добавьте фотографии отеля и укажите главную. Этот шаг можно пропустить.'}</p></div>
          {ready && <div className="wizard-photo-area admin-form"><HotelPhotoEditor key={createdHotel.id} hotelId={createdHotel.id} hotelName={draft.name} onBusyChange={setPhotoBusy} onPhotosChanged={() => loadPhotos(createdHotel.id).catch(err => setError(`Не удалось обновить данные фотографий: ${err.message}`))} /></div>}
        </>}
        {step === 3 && <>
          <div className="wizard-section-head"><h3>Шаг 4. Номера</h3><p>{editingMode ? 'Все существующие номера отеля и их фото загружаются из backend. Редактируйте их здесь или добавляйте новые.' : 'Создайте номера и установите цены. Добавлять новые номера можно и после завершения.'}</p></div>
          <div className="wizard-room-top"><strong>Номера отеля ({roomTotal})</strong><Button type="button" size="small" variant="outline" onClick={() => setRoomWizard({mode:'create'})}><Plus size={16}/> Добавить номер</Button></div>
          {rooms.length > 0 && <div className="wizard-room-list">{rooms.map(room => <div className="wizard-room-card" key={room.id}>
            <span className="wizard-room-photo"><RoomPhotoImage roomId={room.id} hotelId={createdHotel?.id} alt={`Фото номера ${room.number}`} /></span>
            <div><b>Номер {room.number}</b><span>{room.category} · {room.capacity} гост. · {formatMoney(room.pricePerNight)} / ночь</span></div>
            <div className="wizard-room-actions"><Button type="button" size="small" variant="outline" onClick={() => setRoomWizard({mode:'edit', room, initialStep:1})}><ImagePlus size={14}/> Фотографии</Button><Button type="button" size="small" variant="outline" onClick={() => setRoomWizard({mode:'edit', room, initialStep:0})}><Pencil size={14}/> Редактировать</Button></div>
          </div>)}</div>}
          <Pagination page={roomPage} totalPages={roomPages} onChange={p=>loadRooms(createdHotel.id,p).catch(err=>setError(err.message))}/>
          {!roomTotal && <div className="wizard-empty"><BedDouble size={30}/><p>Номеров пока нет. Добавьте первый номер через мастер.</p></div>}
        </>}
        {step === 4 && <>
          <div className="wizard-section-head"><h3>Шаг 5. Проверка</h3><p>{editingMode ? 'Проверьте сохранённые фотографии, номера и настройку стратегии.' : 'Проверьте информацию перед завершением работы мастера.'}</p></div>
          <div className="wizard-review">
            <div className="wizard-review-photo">{ready ? <HotelReviewGallery hotelId={previewId} hotelName={draft.name} photos={hotelPhotos} /> : <HotelNoPhotos/>}</div>
            <div className="wizard-review-details"><h3>{draft.name}</h3><p>{draft.address}</p>
              <dl><div><dt>Автоподбор</dt><dd>{STRATEGIES.find(item => item.value === draft.roomSelectionStrategy)?.label}</dd></div><div><dt>Рейтинг</dt><dd>{draft.rating === '' ? 'Не задан' : `${draft.rating} / 5`}</dd></div><div><dt>Заезд / выезд</dt><dd>{draft.checkInTime} / {draft.checkOutTime}</dd></div><div><dt>Фотографии</dt><dd>{photoCount}</dd></div><div><dt>Номера</dt><dd>{roomTotal}</dd></div></dl>
              {draft.description && <p className="wizard-review-description">{draft.description}</p>}
              {allAmenities.length > 0 && <div className="wizard-review-amenities">{allAmenities.map(a => <span key={a}><CheckCircle2 size={14}/>{a}</span>)}</div>}
            </div>
          </div>
          {roomTotal > 0 && <div className="wizard-review-roomlist">
            <h4>{editingMode ? `Номера отеля (всего ${roomTotal})` : `Добавленные номера (всего ${roomTotal})`}</h4><p className="muted smaller">Отображаются номера текущей страницы. Остальные доступны через переключение страниц.</p>
            {rooms.map(room => <section className="wizard-review-room" key={room.id} aria-label={`Номер ${room.number}`}>
              <div className="wizard-review-room-info">
                <strong>Номер {room.number}</strong>
                <span>{room.category} · {room.capacity} гостей</span>
                <b>{formatMoney(room.pricePerNight)} / ночь</b>
              </div>
              <RoomPreviewSlider roomId={room.id} number={room.number} showAllThumbnails/>
            </section>)}
          </div>}
          <Pagination page={roomPage} totalPages={roomPages} onChange={p=>loadRooms(createdHotel.id,p).catch(err=>setError(err.message))}/>
          {roomTotal === 0 && <div className="wizard-warning"><Info size={18}/> В отеле пока нет номеров — бронирование станет доступно после их добавления.</div>}
          <div className="wizard-done-info"><CheckCircle2 size={19}/><span>{editingMode ? 'Изменения сохранены в backend. Нажмите «Завершить», чтобы вернуться к каталогу.' : 'Отель и сохранённые данные находятся в backend. Кнопка «Завершить» закроет мастер.'}</span></div>
        </>}
        {(error || notice) && <div className="wizard-status"><Alert error={error} success={!error ? notice : undefined}/></div>}
        </>}
      </div>
      <footer className="hotel-wizard-footer">
        <span>Шаг {step + 1} из {WIZARD_STEPS.length}{ready && <small> · Отель #{createdHotel.id} сохранён</small>}</span>
        <div className="wizard-footer-actions">
          {step === 0 ? <Button variant="outline" type="button" disabled={initializing || busy || photoBusy} onClick={closeWizard}>{editingMode ? 'Закрыть' : 'Отмена'}</Button>
            : <Button variant="outline" type="button" disabled={initializing || busy || photoBusy} onClick={backStep}><ArrowLeft size={16}/> Назад</Button>}
          <Button type={step === 0 || step === 1 ? 'submit' : 'button'} form={step === 0 || step === 1 ? 'wizard-main-form' : undefined}
            disabled={initializing || loadFailure || busy || photoBusy} onClick={step > 1 ? nextStep : undefined}>
            {busy ? 'Сохраняем...' : step === 4 ? 'Завершить' : editingMode && (step === 0 || step === 1) ? 'Сохранить и далее' : step === 1 && !createdHotel ? 'Создать отель и далее' : 'Далее'}
            {step === 4 ? <Check size={17}/> : <ArrowRight size={17}/>}
          </Button>
        </div>
      </footer>
    </section>
  </div>
  {roomWizard && createPortal(<RoomCreationWizard
    key={`${roomWizard.mode}-${roomWizard.room?.id || 'new'}`}
    nested hotelId={createdHotel.id} hotelName={draft.name}
    roomToEdit={roomWizard.room || null} initialStep={roomWizard.initialStep || 0}
    onSaved={() => loadRooms(createdHotel.id).catch(err => setError(err.message))}
    onClose={() => { setRoomWizard(null); loadRooms(createdHotel.id).catch(err => setError(err.message)) }}
  />, document.body)}
  </>
}

export function AdminHotels() {
  const [hotels, setHotels] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [wizard, setWizard] = useState(null)
  const [refreshVersion, setRefreshVersion] = useState(0)
  const [message, setMessage] = useState('')
  const [listPage,setListPage] = useState(0)
  const [listPages,setListPages] = useState(1)
  const HOTEL_LIST_PAGE_SIZE=20

  useEffect(()=>{
    let active=true
    const controller=new AbortController()
    setLoading(true);setLoadError('')
    api.hotelsPage({page:listPage,size:HOTEL_LIST_PAGE_SIZE}, {signal:controller.signal,forceRefresh:refreshVersion>0}).then(result=>{
      if(!active)return
      setHotels(arrayOf(result));setListPages(Math.max(1,Number(result.totalPages||1)))
    }).catch(err=>{if(active&&err.name!=='AbortError')setLoadError(`${err.message} Показан ранее загруженный список, если он доступен.`)})
      .finally(()=>{if(active)setLoading(false)})
    return()=>{active=false;controller.abort()}
  },[listPage,refreshVersion])
  function wizardDone(hotel) {
    const edited=wizard?.mode==='edit'
    setWizard(null)
    setRefreshVersion(v=>v+1)
    setMessage(edited?`Отель «${hotel.name}» обновлён.`:`Отель «${hotel.name}» добавлен.`)
  }
  function wizardClosed() {
    setWizard(null)
    setRefreshVersion(v=>v+1)
  }

  return <>
    <PageTitle eyebrow="КАТАЛОГ" title="Управление отелями"
      subtitle="Создавайте и редактируйте отели через пятишаговый мастер."
      action={<Button type="button" onClick={() => { setMessage(''); setWizard({mode:'create'}) }}><Plus size={17}/> Добавить отель</Button>}/>
    <Alert error={loadError} success={message}/>
    {wizard && createPortal(<HotelWizard
      key={wizard.mode === 'edit' ? `edit-${wizard.hotel.id}` : 'create'}
      hotelToEdit={wizard.mode === 'edit' ? wizard.hotel : null}
      onClose={wizardClosed}
      onComplete={wizardDone}
    />, document.body)}

    {loading ? <Spinner/> : <div className="admin-hotel-list">
      {hotels.map((hotel,index) => <div className="admin-hotel-row" key={hotel.id}>
        <ImageBlock retryable key={`${hotel.id}-${refreshVersion}`} hotelId={hotel.id} fallback={index} alt={hotel.name}/>
        <div><b>{hotel.name}</b><p>{hotel.address || hotel.city}</p>
          <small>ID: {hotel.id} · {hotel.rating != null ? `Рейтинг: ${hotel.rating}` : 'Без рейтинга'}</small>
        </div>
        <Button type="button" variant="outline" size="small" onClick={() => { setMessage(''); setWizard({mode:'edit',hotel}) }}>
          <Pencil size={15}/> Редактировать
        </Button>
      </div>)}
      {hotels.length === 0 && <Empty icon={Hotel} title="Отелей пока нет" text="Для демоданных проверьте APP_DEMO_ENABLED=true в запуске Hotel Service." action={<Button type="button" onClick={() => setWizard({mode:'create'})}>Создать первый отель</Button>}/>}
      <Pagination page={listPage} totalPages={listPages} onChange={setListPage}/>
    </div>}
  </>
}
