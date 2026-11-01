import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'
import { ArrowRight, BarChart3, BedDouble, CalendarDays, ChartNoAxesColumn, CheckCircle2, FileImage, Hotel, ImagePlus, Pencil, Plus, RefreshCcw, Trash2, Upload, UsersRound } from 'lucide-react'
import { api } from '../api/client.js'
import { RoomPhotoImage } from '../components/RoomPhotos.jsx'
import { RoomCreationWizard } from '../components/RoomCreationWizard.jsx'
import { Alert, Amenities, Button, Empty, ImageBlock, PageTitle, Spinner, Status } from '../components/Shared.jsx'
import { arrayOf, formatDate, formatMoney, normalRoom } from '../utils/format.js'

function useHotels(){const [hotels,setHotels]=useState([]);const [loading,setLoading]=useState(true);const [error,setError]=useState('');async function reload(){setLoading(true);setError('');try{setHotels(arrayOf(await api.hotelsAll()))}catch(e){setError(e.message)}finally{setLoading(false)}}useEffect(()=>{reload()},[]);return {hotels,loading,error,reload}}
export function AdminOverview(){const {hotels,loading,error}=useHotels();return <><PageTitle eyebrow="ADMIN PANEL" title="Панель управления" subtitle="Управление гостиничным каталогом и заказами."/><div className="stat-cards"><div><span className="stat-icon blue"><Hotel size={23}/></span><b>{hotels.length}</b><span>Отелей в каталоге</span></div><div><span className="stat-icon green"><BedDouble size={23}/></span><b>—</b><span>Количество номеров — в разделе «Номера»</span></div><div><span className="stat-icon orange"><BarChart3 size={23}/></span><b>—</b><span>Метрики — в разделе «Статистика»</span></div></div><Alert error={error}/><div className="section-top dashboard-section"><div><h2>Быстрый доступ</h2><p>Операции с каталогом и заказами</p></div></div><div className="admin-actions-grid">{[['/admin/hotels',Hotel,'Отели','Создавать отели и изменять описание'],['/admin/rooms',BedDouble,'Номера','Управление категориями, ценами и удобствами'],['/admin/users',UsersRound,'Пользователи','Просмотр и управление учётными записями'],['/admin/bookings',CalendarDays,'Бронирования','Просмотр бронирований'],['/admin/statistics',ChartNoAxesColumn,'Статистика','Показатели по отелям']].map(([to,Icon,label,text])=><Link to={to} key={to} className="admin-action"><span><Icon size={22}/></span><b>{label}</b><p>{text}</p><ArrowRight size={17} className="admin-action-arrow"/></Link>)}</div>{loading&&<Spinner/>}</>}
export function AdminRooms() {
  const { hotels, error: hotelsError } = useHotels()
  const [hotelId, setHotelId] = useState('')
  const [rooms, setRooms] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [wizard, setWizard] = useState(null)
  useEffect(() => { if (!hotelId && hotels.length) setHotelId(String(hotels[0].id)) }, [hotels, hotelId])
  async function reload() {
    if (!hotelId) return
    setLoading(true)
    try { setRooms(arrayOf(await api.roomsAll(hotelId)).map(normalRoom)); setError('') }
    catch (err) { setError(err.message) }
    finally { setLoading(false) }
  }
  useEffect(() => { reload() }, [hotelId])
  const hotel = hotels.find(h => String(h.id) === hotelId)
  return <>
    <PageTitle eyebrow="НОМЕРНОЙ ФОНД" title="Управление номерами" subtitle="Все изменения — в мастере поверх страницы, без форм внизу."
      action={<Button type="button" disabled={!hotelId} onClick={() => setWizard({ mode:'create' })}><Plus size={17}/> Добавить номер</Button>}/>
    <div className="admin-filter"><label>Отель<select value={hotelId} onChange={e => {setHotelId(e.target.value); setWizard(null)}}>
      {hotels.map(h => <option key={h.id} value={h.id}>{h.name}</option>)}
    </select></label><Button type="button" variant="outline" onClick={reload}><RefreshCcw size={16}/> Обновить</Button></div>
    <Alert error={hotelsError || error}/>
    {wizard && createPortal(<RoomCreationWizard
      key={wizard.room?.id || 'create'} hotelId={hotelId} hotelName={hotel?.name}
      roomToEdit={wizard.room || null} initialStep={wizard.initialStep || 0}
      onSaved={() => reload()}
      onClose={() => {setWizard(null);reload()}}
    />, document.body)}
    {loading ? <Spinner/> : <div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Номер</th><th>Категория</th><th>Гости</th><th>Цена за ночь</th><th>Удобства</th><th>Фото</th><th>Действия</th></tr></thead>
      <tbody>{rooms.map(room => <tr key={room.id}>
        <td><b>№ {room.number}</b><small>ID {room.id}</small></td><td>{room.category}</td><td>{room.capacity}</td><td>{formatMoney(room.pricePerNight)}</td><td><Amenities items={room.amenities} limit={2}/></td>
        <td className="room-admin-preview"><RoomPhotoImage roomId={room.id} hotelId={hotelId} alt={`Номер ${room.number}`}/></td>
        <td className="room-admin-controls">
          <Button type="button" size="small" variant="outline" onClick={() => setWizard({mode:'edit',room,initialStep:0})}><Pencil size={15}/> Изменить</Button>
          <Button type="button" size="small" variant="outline" onClick={() => setWizard({mode:'edit',room,initialStep:1})}><ImagePlus size={15}/> Фотографии</Button>
        </td>
      </tr>)}</tbody></table>
      {rooms.length === 0 && <Empty title="Для этого отеля нет номеров" action={<Button type="button" onClick={() => setWizard({mode:'create'})}>Создать номер</Button>}/>}</div>}
  </>
}
export function AdminPhotos() {
  const {hotels,error:hotelsError}=useHotels()
  const [hotelId,setHotelId]=useState('')
  const [photos,setPhotos]=useState([])
  const [visibleCount,setVisibleCount]=useState(12)
  const [loading,setLoading]=useState(false)
  const [saving,setSaving]=useState(false)
  const [error,setError]=useState('')
  const [success,setSuccess]=useState('')
  useEffect(()=>{if (!hotelId && hotels.length) setHotelId(String(hotels[0].id))},[hotels,hotelId])
  async function load(){
    if(!hotelId)return
    setLoading(true)
    try{setPhotos(arrayOf(await api.photos(hotelId)).sort((a,b)=>Number(b.primary)-Number(a.primary)||Number(a.sortOrder??0)-Number(b.sortOrder??0)));setError('')}
    catch(e){setError(e.message)}finally{setLoading(false)}
  }
  useEffect(()=>{setVisibleCount(12);load()},[hotelId])
  async function perform(fn,successText){
    setSaving(true);setError('');setSuccess('')
    try{await fn();await load();setSuccess(successText)}catch(e){setError(e.message)}finally{setSaving(false)}
  }
  async function upload(e){
    const files=Array.from(e.target.files||[])
    if(!files.length)return
    for(const f of files){
      if(!['image/jpeg','image/png'].includes(f.type)||f.size>5*1024*1024){setError('Разрешены JPEG/PNG не более 5 МБ на файл.');e.target.value='';return}
    }
    await perform(async()=>{for(const f of files)await api.photoUpload(hotelId,f)},'Фотографии загружены.')
    e.target.value=''
  }
  const visible=photos.slice(0,visibleCount)
  return <>
    <PageTitle eyebrow="МЕДИАТЕКА" title="Фотографии отелей" subtitle="Существующие фото и управление главной фотографией."/>
    <div className="admin-filter"><label>Отель<select value={hotelId} onChange={e=>setHotelId(e.target.value)}>{hotels.map(h=><option key={h.id} value={h.id}>{h.name}</option>)}</select></label><Button variant="outline" onClick={load}><RefreshCcw size={16}/> Обновить</Button></div>
    <Alert error={hotelsError||error} success={success}/>
    <div className="gallery-more-actions"><b>Всего фотографий: {photos.length}</b>{photos.length>0&&<span>Показано {visible.length} из {photos.length}</span>}</div>
    <label className="upload-box"><Upload size={30}/><b>Загрузить фотографии</b><span>JPEG / PNG, до 5 МБ на файл</span><input disabled={saving||!hotelId} type="file" accept="image/png,image/jpeg" multiple onChange={upload}/></label>
    {loading?<Spinner/>:photos.length?<>
      <div className="photo-admin-grid">{visible.map((photo,i)=><div className="photo-admin-card" key={photo.id}>
        <ImageBlock hotelId={hotelId} photoId={photo.id} fallback={i}/>
        <div><span>Фото {i+1} из {photos.length} {photo.primary&&<strong className="primary-label">Главное</strong>}</span>
          <div className="photo-admin-actions"><Button size="small" variant="outline" disabled={saving||photo.primary} onClick={()=>perform(()=>api.photoPrimary(hotelId,photo.id),'Главная фотография изменена.')}><CheckCircle2 size={14}/> Главное</Button>
            <Button size="small" variant="danger-light" disabled={saving} onClick={()=>{if(window.confirm('Удалить фотографию?'))perform(()=>api.photoDelete(hotelId,photo.id),'Фотография удалена.')}} aria-label="Удалить фото"><Trash2 size={14}/></Button>
          </div>
        </div>
      </div>)}</div>
      {photos.length>visibleCount&&<div className="gallery-more-actions"><Button variant="outline" onClick={()=>setVisibleCount(n=>n+12)}>Показать ещё ({photos.length-visibleCount})</Button></div>}
    </>:<Empty icon={FileImage} title="Фотографий пока нет" text="В API отеля пока нет фотографий. Проверьте демозагрузку Hotel Service."/>}
  </>
}
export function AdminBookings(){const [bookings,setBookings]=useState([]);const [status,setStatus]=useState('ALL');const [error,setError]=useState('');const [loading,setLoading]=useState(true);const [page,setPage]=useState(0);const [totalPages,setTotalPages]=useState(1);async function load(){setLoading(true);setError('');try{const data=await api.bookingsAdmin({page,size:20,...(status==='ALL'?{}:{status})});setBookings(arrayOf(data));setTotalPages(data?.totalPages||1)}catch(e){setError(e.message)}finally{setLoading(false)}}useEffect(()=>{load()},[status,page]);return <><PageTitle eyebrow="ЗАКАЗЫ" title="Все бронирования" subtitle="Статусы бронирований в Booking Service."/><div className="admin-filter"><label>Статус<select value={status} onChange={e=>{setStatus(e.target.value);setPage(0)}}><option value="ALL">Все</option>{['CONFIRMED','CANCELLED','COMPLETED','PENDING','FAILED'].map(s=><option key={s}>{s}</option>)}</select></label><Button variant="outline" onClick={load}><RefreshCcw size={16}/> Обновить</Button></div><Alert error={error}/>{loading?<Spinner/>:<div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Заказ</th><th>Пользователь</th><th>Номер</th><th>Заезд / выезд</th><th>Статус</th></tr></thead><tbody>{bookings.map(b=><tr key={b.id}><td><b>#{b.id}</b></td><td>{b.userEmail||b.userId||'—'}</td><td>#{b.roomId||'—'}</td><td>{formatDate(b.checkIn)} — {formatDate(b.checkOut)}</td><td><Status value={b.status}/></td></tr>)}</tbody></table>{bookings.length===0&&<Empty title="Бронирования отсутствуют"/>}<div className="pagination">{page>0&&<Button variant="outline" size="small" onClick={()=>setPage(p=>p-1)}>Назад</Button>}{totalPages>1&&<span>Страница {page+1} / {totalPages}</span>}{page+1<totalPages&&<Button variant="outline" size="small" onClick={()=>setPage(p=>p+1)}>Далее</Button>}</div></div>}</>}
export function AdminStatistics(){const {hotels}=useHotels();const [hotelId,setHotelId]=useState('');const [stats,setStats]=useState(null);const [error,setError]=useState('');const [loading,setLoading]=useState(false);useEffect(()=>{if(!hotelId&&hotels.length)setHotelId(String(hotels[0].id))},[hotels,hotelId]);useEffect(()=>{if(!hotelId)return;let active=true;setLoading(true);setError('');api.statistics(hotelId).then(s=>{if(active)setStats(s)}).catch(e=>{if(active)setError(e.message)}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[hotelId]);const labels={totalRooms:'Всего номеров',availableRooms:'Доступно номеров',totalBookings:'Всего бронирований',confirmedBookings:'Подтверждено',cancelledBookings:'Отменено',occupancyRate:'Загрузка, %',timesBooked:'Количество броней',totalRevenue:'Выручка (если рассчитывается)',hotelId:'ID отеля'};const metrics=Object.entries(stats||{}).filter(([,v])=>typeof v==='number'||typeof v==='string'||typeof v==='boolean');return <><PageTitle eyebrow="АНАЛИТИКА" title="Статистика отелей" subtitle="Актуальные показатели Hotel Service, без имитации данных."/><div className="admin-filter"><label>Выберите отель<select value={hotelId} onChange={e=>setHotelId(e.target.value)}>{hotels.map(h=><option key={h.id} value={h.id}>{h.name}</option>)}</select></label></div><Alert error={error}/>{loading?<Spinner/>:stats?<><div className="stat-cards">{metrics.map(([k,v],i)=><div key={k}><span className={`stat-icon ${['blue','green','orange'][i%3]}`}><BarChart3 size={22}/></span><b>{String(v)}</b><span>{labels[k]||k.replace(/([A-Z])/g,' $1')}</span></div>)}</div>{metrics.length===0&&<div className="white-card"><h3>Ответ сервера</h3><pre className="data-preview">{JSON.stringify(stats,null,2)}</pre></div>}</>:<Empty icon={ChartNoAxesColumn} title="Выберите отель для просмотра статистики"/>}</>}
