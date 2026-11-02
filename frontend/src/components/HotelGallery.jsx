import { useEffect, useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import { ChevronDown, ChevronLeft, ChevronRight, ChevronUp, Images, X } from 'lucide-react'
import { Button, HotelNoPhotos, ImageBlock } from './Shared.jsx'
import { useModalFocus } from '../utils/useModalFocus.js'

// Only four sidebar thumbnails and twelve lightbox thumbnails are mounted at a time.
// The photo metadata may contain 50+ entries without loading all image files.
const PAGE_SIZE = 12
const SIDE_SIZE = 4

export function HotelGallery({ hotelId, hotelName, photos = [], error = '' }) {
  const sorted = useMemo(() => [...photos].sort((a, b) => Number(b.primary) - Number(a.primary) || Number(a.sortOrder ?? 0) - Number(b.sortOrder ?? 0)), [photos])
  const [selected, setSelected] = useState(0)
  const [sideStart, setSideStart] = useState(1)
  const [open, setOpen] = useState(false)
  const modalRef = useModalFocus(open,()=>setOpen(false))
  const [page, setPage] = useState(0)

  useEffect(() => { setSelected(0); setSideStart(1); setPage(0); setOpen(false) }, [hotelId])
  useEffect(() => { if (selected >= sorted.length && sorted.length) setSelected(0) }, [selected, sorted.length])
  const total = sorted.length
  const current = sorted[selected] || sorted[0]
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))
  const onPage = sorted.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  const sideIndices = useMemo(() => {
    const result = []
    for (let offset = 0; offset < total && result.length < SIDE_SIZE; offset++) {
      const index = (sideStart + offset) % total
      if (index !== selected) result.push(index)
    }
    return result
  }, [sideStart, selected, total])

  function changeSelected(index) {
    if (!total) return
    const next = ((index % total) + total) % total
    setSelected(next)
    setPage(Math.floor(next / PAGE_SIZE))
  }
  function scrollSide(direction) {
    if (total <= SIDE_SIZE + 1) return
    setSideStart(start => ((start + direction) % total + total) % total)
  }

  useEffect(() => {
    if (!open || !total) return
    const onKey = event => {
      if (event.key === 'Escape') setOpen(false)
      if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
        event.preventDefault()
        setSelected(previous => {
          const next = (previous + (event.key === 'ArrowRight' ? 1 : -1) + total) % total
          setPage(Math.floor(next / PAGE_SIZE))
          return next
        })
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, total])

  if (!total) return <div className="hotel-gallery-blank"><HotelNoPhotos message={error ? 'Не удалось загрузить фотографии отеля' : 'Нет фотографий отеля'}/></div>

  return <>
    <section className="hotel-gallery-showcase" aria-label={`Фотографии отеля ${hotelName}`}>
      <button type="button" className="hotel-gallery-hero hover-photo-zoom" onClick={() => setOpen(true)} aria-label={`Открыть галерею отеля: ${total} фото`}>
        <ImageBlock hotelId={hotelId} photoId={current?.id} alt={`${hotelName}, фото ${selected + 1}`}/>
        <span className="hotel-gallery-hero-count"><Images size={16}/> {selected + 1} / {total}</span>
      </button>
      <div className="hotel-gallery-side" aria-label="Миниатюры фотографий отеля" onWheel={event => {
        if (Math.abs(event.deltaY) > 4) scrollSide(event.deltaY > 0 ? 1 : -1)
      }}>
        <div className="hotel-gallery-side-viewport">
          {sideIndices.map(index => <button type="button" className="hotel-gallery-side-photo hover-photo-zoom" key={sorted[index].id} onClick={() => { changeSelected(index); setSideStart((index + 1) % total) }} aria-label={`Показать фотографию ${index + 1} из ${total}`}>
            <ImageBlock hotelId={hotelId} photoId={sorted[index].id} alt={`${hotelName}, фото ${index + 1}`}/>
          </button>)}
        </div>
        {total > SIDE_SIZE + 1 && <>
          <button type="button" className="hotel-side-scroll hotel-side-scroll-up" onClick={() => scrollSide(-1)} aria-label="Предыдущие миниатюры"><ChevronUp size={18}/></button>
          <button type="button" className="hotel-side-scroll hotel-side-scroll-down" onClick={() => scrollSide(1)} aria-label="Следующие миниатюры"><ChevronDown size={18}/></button>
        </>}
      </div>
      <button type="button" className="hotel-gallery-open" onClick={() => setOpen(true)}><Images size={16}/> Все фотографии ({total})</button>
    </section>
    {open && createPortal(<div className="room-lightbox hotel-photo-lightbox" role="presentation" onClick={() => setOpen(false)}>
      <section className="room-lightbox-dialog hotel-photo-lightbox-dialog" ref={modalRef} tabIndex={-1} role="dialog" aria-modal="true" aria-label={`Галерея отеля ${hotelName}`} onClick={event => event.stopPropagation()}>
        <header className="room-lightbox-header"><strong>{hotelName} · фото {selected + 1} из {total}</strong><button type="button" aria-label="Закрыть галерею" onClick={() => setOpen(false)}><X size={23}/></button></header>
        <div className="room-lightbox-photo"><ImageBlock key={current?.id} hotelId={hotelId} photoId={current?.id} alt={`${hotelName}, фото ${selected + 1}`}/></div>
        <div className="room-lightbox-controls"><Button variant="outline" onClick={() => changeSelected(selected - 1)}><ChevronLeft size={18}/> Назад</Button><Button variant="outline" onClick={() => changeSelected(selected + 1)}>Вперёд <ChevronRight size={18}/></Button></div>
        <div className="hotel-gallery-page-heading"><b>Фотографии</b><span>{page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, total)} из {total}</span></div>
        <div className="hotel-gallery-photo-page">{onPage.map((photo, i) => <button type="button" className={photo.id === current?.id ? 'selected hover-photo-zoom' : 'hover-photo-zoom'} key={photo.id} onClick={() => changeSelected(page * PAGE_SIZE + i)} aria-label={`Выбрать фото ${page * PAGE_SIZE + i + 1}`}><ImageBlock hotelId={hotelId} photoId={photo.id} alt={`Фото ${page * PAGE_SIZE + i + 1}`}/></button>)}</div>
        {totalPages > 1 && <div className="hotel-gallery-page-controls"><Button variant="outline" disabled={page === 0} onClick={() => changeSelected((page - 1) * PAGE_SIZE)}>Предыдущие</Button><span>Страница {page + 1} из {totalPages}</span><Button variant="outline" disabled={page + 1 >= totalPages} onClick={() => changeSelected((page + 1) * PAGE_SIZE)}>Следующие</Button></div>}
      </section>
    </div>, document.body)}
  </>
}
