import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { CheckCircle2, ChevronLeft, ChevronRight, ImageOff, ImagePlus, RefreshCcw, Trash2, Upload, X } from 'lucide-react'
import { api, imageBlob } from '../api/client.js'
import { Alert, Button, ImageBlock, Spinner } from './Shared.jsx'
import { arrayOf } from '../utils/format.js'
import { cachedRoomPhotos } from '../utils/detailCache.js'
import { useModalFocus } from '../utils/useModalFocus.js'

const LIMIT = 10
const MAX_SIZE = 5 * 1024 * 1024
const TYPES = ['image/jpeg', 'image/png', 'image/webp']

// A room without photos must never look as if it has the hotel's cover image.
export function RoomNoPhotos({ compact = false, message = 'Нет фотографий номера' }) {
  return <div className={`room-no-photos ${compact ? 'room-no-photos-compact' : ''}`} role="status">
    <ImageOff size={compact ? 22 : 34} aria-hidden="true"/>
    <span>{message}</span>
  </div>
}

export function RoomPhotoImage({ roomId, photoId, hotelId, className = '', alt = 'Фотография номера', fallback = 0, fallbackToHotel = false, retryable = false }) {
  const [state, setState] = useState({ status: 'loading', url: null })
  const [retry,setRetry] = useState(0)
  const rootRef=useRef(null)
  const [visible,setVisible]=useState(()=>typeof IntersectionObserver==='undefined')
  useEffect(()=>{
    if(typeof IntersectionObserver==='undefined'){setVisible(true);return}
    const observer=new IntersectionObserver(entries=>{
      if(entries.some(entry=>entry.isIntersecting)){setVisible(true);observer.disconnect()}
    },{rootMargin:'350px'})
    if(rootRef.current)observer.observe(rootRef.current)
    return()=>observer.disconnect()
  },[])
  useEffect(() => {
    if(!visible)return

    let active = true
    let objectUrl
    const controller = new AbortController()
    setState({ status: 'loading', url: null })
    async function load() {
      if (!roomId) { if (active) setState({ status: 'empty', url: null }); return }
      try {
        let id = photoId
        if (!id) {
          const list = arrayOf(await cachedRoomPhotos(roomId))
          id = (list.find(item => item.primary) || list[0])?.id
        }
        if (!active) return
        if (!id) { setState({ status: 'empty', url: null }); return }
        const blob = await imageBlob(api.roomPhotoFilePath(roomId, id), controller.signal)
        if (!active) return
        objectUrl = URL.createObjectURL(blob)
        setState({ status: 'ready', url: objectUrl })
      } catch {
        if (active) setState({ status: 'error', url: null })
      }
    }
    load()
    return () => { active = false; controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [roomId, photoId, visible, retry])
  if (state.url) return <div ref={rootRef} className={`photo-frame ${className}`}><img src={state.url} alt={alt} loading="lazy" decoding="async"/></div>
  if (fallbackToHotel && hotelId && state.status !== 'loading') return <ImageBlock hotelId={hotelId} className={className} alt={alt} fallback={fallback}/>
  return <div ref={rootRef} className={`photo-frame room-empty-photo-frame ${className}`}>
    <RoomNoPhotos compact={false} message={state.status === 'loading' ? 'Загружаем фото…' : state.status === 'error' ? 'Не удалось загрузить фото' : 'Нет фотографий номера'}/>
    {retryable && state.status === 'error' && <button className="image-retry-control" type="button" onClick={() => setRetry(n => n + 1)} aria-label="Повторить загрузку фотографии">Повторить</button>}
  </div>
}

// One slider for both editing and final review; all photos are reachable by arrows.
// On the final review the thumbnails are scrollable and all of them are shown (up to the
// backend's current per-room limit of 10); browsing does not require another API.
export function RoomPreviewSlider({ roomId, number, initialPhotos, showAllThumbnails = false }) {
  const [photos, setPhotos] = useState([])
  const [index, setIndex] = useState(0)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    let active = true
    setPhotos([]); setIndex(0); setError(''); setLoading(true)
    if (Array.isArray(initialPhotos)) {
      setPhotos([...initialPhotos].sort((a,b) => Number(b.primary)-Number(a.primary) || Number(a.sortOrder??0)-Number(b.sortOrder??0)))
      setLoading(false)
      return () => { active = false }
    }
    if (!roomId) { setLoading(false); return () => { active = false } }
    api.roomPhotos(roomId).then(data => {
      if (active) setPhotos(arrayOf(data).sort((a,b) => Number(b.primary)-Number(a.primary) || Number(a.sortOrder??0)-Number(b.sortOrder??0)))
    }).catch(err => { if (active) setError(err.message) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [roomId, initialPhotos])
  const shown = photos[index]
  const first = Math.max(0, Math.min(index - 2, photos.length - 5))
  const thumbs = showAllThumbnails ? photos : photos.slice(first, first + 5)
  return <div className="room-wizard-slider" aria-label={`Фотографии номера ${number}`}>
    <div className="room-wizard-slider-image hover-photo-zoom">
      {shown ? <RoomPhotoImage roomId={roomId} photoId={shown.id} alt={`Номер ${number}, фото ${index + 1}`}/>
        : <RoomNoPhotos message={loading ? 'Загружаем фотографии номера…' : error ? 'Не удалось загрузить фотографии номера' : 'Нет фотографий номера'}/>}
      {photos.length > 1 && <>
        <button type="button" className="room-slider-prev" aria-label="Предыдущее фото" onClick={() => setIndex(i => (i + photos.length - 1) % photos.length)}><ChevronLeft size={20}/></button>
        <button type="button" className="room-slider-next" aria-label="Следующее фото" onClick={() => setIndex(i => (i + 1) % photos.length)}><ChevronRight size={20}/></button>
        <span className="room-slider-count" aria-live="polite">{index + 1} / {photos.length}</span>
      </>}
    </div>
    {photos.length > 0 && <div className="room-wizard-slider-meta">{photos.length} {photos.length === 1 ? 'фотография' : photos.length < 5 ? 'фотографии' : 'фотографий'} номера</div>}
    {photos.length > 1 && <div className={`room-wizard-slider-thumbs ${showAllThumbnails ? 'room-wizard-slider-thumbs-all' : ''}`} aria-label={`Все фотографии номера ${number}`}>
      {thumbs.map((photo, i) => {
        const photoIndex = showAllThumbnails ? i : first + i
        return <button type="button" key={photo.id} className={index === photoIndex ? 'selected' : ''} onClick={() => setIndex(photoIndex)} aria-label={`Показать фото ${photoIndex + 1} из ${photos.length}`} aria-pressed={index === photoIndex}><RoomPhotoImage roomId={roomId} photoId={photo.id}/></button>
      })}
    </div>}
    {error && <small role="alert" className="room-photo-load-error">Не удалось получить фотографии: {error}</small>}
  </div>
}

// Read-only gallery for the room card and for the booking details.
export function RoomGallery({ roomId, hotelId, number, compact = false }) {
  const rootRef = useRef(null)
  const [nearViewport, setNearViewport] = useState(() => typeof IntersectionObserver === 'undefined')
  const [photos, setPhotos] = useState([])
  const [selected, setSelected] = useState(0)
  const [open, setOpen] = useState(false)
  const [error,setError] = useState('')
  const [retry,setRetry] = useState(0)
  const modalRef = useModalFocus(open, () => setOpen(false))
  useEffect(() => {
    if (nearViewport) return
    if (typeof IntersectionObserver === 'undefined') { setNearViewport(true); return }
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) {
        setNearViewport(true)
        observer.disconnect()
      }
    }, { rootMargin: '280px' })
    if (rootRef.current) observer.observe(rootRef.current)
    return () => observer.disconnect()
  }, [nearViewport])
  useEffect(() => {
    if (!open || photos.length < 2) return
    const onKey = event => {
      if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
      event.preventDefault()
      setSelected(index => (index + (event.key === 'ArrowRight' ? 1 : -1) + photos.length) % photos.length)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open,photos.length])
  useEffect(() => {
    let active = true
    const controller = new AbortController()
    setError('')
    setPhotos([])
    setSelected(0)
    if (!roomId || !nearViewport) return () => { active = false }
    api.roomPhotos(roomId, { signal: controller.signal }).then(data => {
      if (active) setPhotos(arrayOf(data).sort((a, b) => Number(a.sortOrder ?? 0) - Number(b.sortOrder ?? 0)))
    }).catch(err => {if(active && err.name !== 'AbortError')setError(err.message)})
    return () => { active = false; controller.abort() }
  }, [roomId, retry, nearViewport])
  const main = photos[selected] || photos[0]
  return <div ref={rootRef} className={`room-photo-gallery ${compact ? 'room-photo-gallery-compact' : ''}`}>
    <button className="room-gallery-main hover-photo-zoom" type="button" aria-label={photos.length ? `Открыть галерею фотографий номера ${number || roomId} (${photos.length} фото)` : `Нет фотографий номера ${number || roomId}`} title={photos.length ? 'Посмотреть фотографии номера' : 'Фотографии номера пока отсутствуют'} onClick={() => photos.length && setOpen(true)}>
      {nearViewport ? <RoomPhotoImage roomId={roomId} hotelId={hotelId} photoId={main?.id} alt={`Номер ${number || ''}`} /> : <RoomNoPhotos message="Загружаем фотографии при прокрутке…"/>}
      {photos.length > 0 && <span className="room-gallery-count"><ImagePlus size={14} /> Смотреть фото ({photos.length})</span>}
    </button>
    {error && <div className="photo-retry" role="alert">Не удалось загрузить фотографии: {error} <Button size="small" variant="outline" onClick={()=>setRetry(v=>v+1)}>Повторить</Button></div>}
    {!compact && photos.length > 1 && <div className="room-gallery-thumbs">{photos.slice(0, 5).map((photo, i) => <button type="button" key={photo.id} className={selected === i ? 'selected' : ''} onClick={() => setSelected(i)} aria-label={`Фотография ${i + 1}`}><RoomPhotoImage roomId={roomId} photoId={photo.id} /></button>)}</div>}
    {open && createPortal(<div className="room-lightbox" role="presentation" onClick={() => setOpen(false)}><div ref={modalRef} tabIndex={-1} role="dialog" aria-modal="true" aria-label={`Фотографии номера ${number || ''}`} className="room-lightbox-dialog" onClick={e => e.stopPropagation()}><div className="room-lightbox-header"><strong>Номер {number || roomId} · {selected + 1} из {photos.length}</strong><button type="button" onClick={() => setOpen(false)} aria-label="Закрыть"><X size={22}/></button></div><div className="room-lightbox-photo"><RoomPhotoImage roomId={roomId} photoId={main?.id}/></div>{photos.length > 1 && <><div className="room-lightbox-controls"><Button type="button" variant="outline" onClick={() => setSelected(i => (i + photos.length - 1) % photos.length)}><ChevronLeft size={18}/> Назад</Button><Button type="button" variant="outline" onClick={() => setSelected(i => (i + 1) % photos.length)}>Вперёд <ChevronRight size={18}/></Button></div><div className="room-lightbox-thumbnails" aria-label="Выбрать фотографию номера">{photos.map((photo,i)=><button key={photo.id} type="button" className={i===selected?'selected':''} aria-label={`Фотография ${i+1} из ${photos.length}`} aria-pressed={i===selected} onClick={()=>setSelected(i)}><RoomPhotoImage roomId={roomId} photoId={photo.id} alt={`Миниатюра ${i+1}`}/></button>)}</div></>}</div></div>, document.body)}
  </div>
}

export function RoomPhotoEditor({ roomId, hotelId, roomNumber, compact = false, onBusyChange }) {
  const [photos, setPhotos] = useState([])
  const [visibleCount, setVisibleCount] = useState(12)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  async function reload() {
    if (!roomId) return
    const list = arrayOf(await api.roomPhotos(roomId))
    setPhotos(list.sort((a, b) => Number(a.sortOrder ?? 0) - Number(b.sortOrder ?? 0)))
  }
  useEffect(() => {
    let active = true
    setLoading(true)
    setPhotos([])
    api.roomPhotos(roomId).then(list => {
      if (active) setPhotos(arrayOf(list).sort((a, b) => Number(a.sortOrder ?? 0) - Number(b.sortOrder ?? 0)))
    }).catch(err => { if (active) setError(err.message) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [roomId])
  async function perform(action, label) {
    if (busy) return
    setBusy(true); onBusyChange?.(true); setError(''); setSuccess('')
    try { await action(); await reload(); setSuccess(label) }
    catch (err) { setError(err.message); try { await reload() } catch { /* unchanged */ } }
    finally { setBusy(false); onBusyChange?.(false) }
  }
  async function movePhoto(index, direction) {
    const target = index + direction
    if (target < 0 || target >= photos.length) return
    const ordered = [...photos]
    ;[ordered[index], ordered[target]] = [ordered[target], ordered[index]]
    await perform(async () => {
      for (let i = 0; i < ordered.length; i++) await api.roomPhotoOrder(roomId, ordered[i].id, i)
    }, 'Порядок фотографий сохранён.')
  }
  async function upload(event) {
    const input = event.target
    const files = Array.from(input.files || [])
    if (!files.length) return
    if (files.length + photos.length > LIMIT) { setError(`Не более ${LIMIT} фотографий на номер.`); input.value = ''; return }
    if (files.some(f => !TYPES.includes(f.type) || f.size > MAX_SIZE || f.size === 0)) { setError('Допустимы JPEG, PNG, WebP до 5 МБ на файл.'); input.value = ''; return }
    let done=0
    await perform(async () => {
      for (const file of files) {
        try {await api.roomPhotoUpload(roomId,file);done++}
        catch(e){throw new Error(`${e.message} Загружено из выбранных: ${done} из ${files.length}.`)}
      }
    }, `Загружено фотографий: ${files.length}.`)
    input.value = ''
  }
  return <section className={`room-photo-editor ${compact ? 'compact' : ''}`}>
    <div className="room-photos-heading"><div><h4><ImagePlus size={18}/> Фотографии номера {roomNumber}</h4><p>Главное фото, порядок, удаление · всего {photos.length} фото (лимит загрузки сейчас {LIMIT})</p></div><Button type="button" size="small" variant="ghost" disabled={busy} onClick={() => perform(async () => {}, 'Галерея обновлена.')}><RefreshCcw size={15}/> Обновить</Button></div>
    <Alert error={error} success={success}/>
    {loading ? <Spinner label="Загружаем фотографии номера..."/> : <div className="room-photo-editor-grid">
      {photos.slice(0,visibleCount).map((photo, index) => <div className="room-photo-admin-tile" key={photo.id}>
        <RoomPhotoImage roomId={roomId} photoId={photo.id} hotelId={hotelId} alt={`Номер ${roomNumber}, фото ${index + 1}`}/>
        <div className="room-photo-admin-meta">{photo.primary ? <span className="photo-main-label"><CheckCircle2 size={13}/> Главное</span> : <button type="button" disabled={busy} onClick={() => perform(() => api.roomPhotoPrimary(roomId, photo.id), 'Главное фото обновлено.')}>Сделать главным</button>}</div>
        <div className="room-photo-admin-tools"><button type="button" disabled={busy || index === 0} title="Переместить раньше" onClick={() => movePhoto(index, -1)}>↑</button><button type="button" disabled={busy || index === photos.length - 1} title="Переместить позже" onClick={() => movePhoto(index, 1)}>↓</button><button type="button" disabled={busy} title="Удалить фото" onClick={() => window.confirm('Удалить фотографию номера?') && perform(() => api.roomPhotoDelete(roomId, photo.id), 'Фотография удалена.')}><Trash2 size={15}/></button></div>
      </div>)}
      {photos.length < LIMIT && <label className={`admin-photo-upload-tile room-photo-upload ${busy ? 'is-disabled' : ''}`}><Upload size={24}/><b>{busy ? 'Загрузка...' : 'Добавить фото'}</b><small>JPEG, PNG, WebP · до 5 МБ</small><input type="file" multiple accept="image/jpeg,image/png,image/webp" disabled={busy} onChange={upload}/></label>}
    </div>}
    {!loading && photos.length > visibleCount && <div className="gallery-more-actions"><Button variant="outline" onClick={() => setVisibleCount(v => v + 12)}>Показать ещё ({photos.length - visibleCount})</Button><span>Показано {Math.min(visibleCount, photos.length)} из {photos.length}</span></div>}
  </section>
}
