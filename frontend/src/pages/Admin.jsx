import { createElement, useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'
import { ArrowRight, BarChart3, BedDouble, CalendarDays, ChartNoAxesColumn, Hotel, ImagePlus, Pencil, Plus, RefreshCcw, UsersRound } from 'lucide-react'
import { api } from '../api/client.js'
import { RoomPhotoImage } from '../components/RoomPhotos.jsx'
import { RoomCreationWizard } from '../components/RoomCreationWizard.jsx'
import { Alert, Amenities, Button, Empty, PageTitle, Spinner, Status, Pagination } from '../components/Shared.jsx'
import { arrayOf, formatDate, formatMoney, normalRoom } from '../utils/format.js'

/** A server-paginated hotel picker shared by Rooms and Statistics. */
function HotelPicker({value,onChange}) {
  const [text,setText]=useState('')
  const [query,setQuery]=useState('')
  const [page,setPage]=useState(0)
  const [hotels,setHotels]=useState([])
  const [pages,setPages]=useState(1)
  const [loading,setLoading]=useState(true)
  const [error,setError]=useState('')
  const [idInput,setIdInput]=useState('')
  const [revision,setRevision]=useState(0)
  useEffect(()=>{
    let active=true
    const controller=new AbortController()
    setLoading(true);setError('')
    api.hotelsPage({page,size:20,...(query?{q:query}:{})},{signal:controller.signal,forceRefresh:revision>0}).then(data=>{
      if(!active)return
      const items=arrayOf(data)
      setHotels(items)
      setPages(Math.max(1,Number(data.totalPages||1)))
    }).catch(e=>{if(active&&e.name!=='AbortError')setError(`${e.message} Отображаемые данные могут быть устаревшими.`)})
      .finally(()=>{if(active)setLoading(false)})
    return()=>{active=false;controller.abort()}
  },[query,page,revision])
  // Auto-select after the page loads; depend explicitly on the latest selection
  // and handler, rather than capturing stale props inside the fetch effect.
  useEffect(()=>{
    if (!value && hotels.length) onChange(String(hotels[0].id), hotels[0])
  },[hotels,value,onChange])
  async function openById(e){
    e.preventDefault()
    const id=Number(idInput)
    if(!Number.isSafeInteger(id)||id<1){setError('Укажите корректный ID отеля.');return}
    try{const found=await api.hotel(id);onChange(String(id),found);setError('')}
    catch(err){setError(err.message)}
  }
  const selectedInList=hotels.some(h=>String(h.id)===String(value))
  return <div className="hotel-picker">
    <div className="hotel-picker-controls">
      <form onSubmit={e=>{e.preventDefault();setPage(0);setQuery(text.trim())}} className="hotel-picker-search">
        <label>Найти отель<input value={text} onChange={e=>setText(e.target.value)} placeholder="Название или адрес"/></label>
        <Button type="submit" variant="outline">Найти</Button>
        {query&&<Button type="button" variant="ghost" onClick={()=>{setText('');setQuery('');setPage(0)}}>Сбросить</Button>}
      </form>
      <form onSubmit={openById} className="hotel-picker-id">
        <label>ID отеля<input value={idInput} onChange={e=>setIdInput(e.target.value)} type="number" min="1" placeholder="ID"/></label>
        <Button type="submit" variant="outline">Открыть ID</Button>
      </form>
      <Button type="button" variant="outline" onClick={()=>setRevision(v=>v+1)}><RefreshCcw size={16}/> Обновить</Button>
    </div>
    <Alert error={error}/>
    {loading?<Spinner/>:<>
      <label className="hotel-picker-select">Выберите отель из результатов
        <select value={selectedInList?String(value):''} onChange={e=>{const h=hotels.find(v=>String(v.id)===e.target.value);if(h)onChange(String(h.id),h)}}>
          {!selectedInList&&<option value="">{value?`Текущий отель #${value} — вне этой страницы`:'Выберите отель'}</option>}
          {hotels.map(h=><option key={h.id} value={h.id}>{h.name} · ID {h.id}</option>)}
        </select>
      </label>
      <Pagination page={page} totalPages={pages} onChange={setPage}/>
    </>}
  </div>
}

export function AdminOverview(){
 const [total,setTotal]=useState(null)
 const [error,setError]=useState('')
 useEffect(()=>{let active=true;api.hotelsPage({page:0,size:1}).then(data=>{if(active)setTotal(Number(data.totalElements||0))}).catch(e=>{if(active)setError(e.message)});return()=>{active=false}},[])
 return <><PageTitle eyebrow="ADMIN PANEL" title="Панель управления" subtitle="Управление гостиничным каталогом и заказами."/>
 <div className="stat-cards"><div><span className="stat-icon blue"><Hotel size={23}/></span><b>{total??'—'}</b><span>Отелей в каталоге</span></div><div><span className="stat-icon green"><BedDouble size={23}/></span><b>—</b><span>Количество номеров — в разделе «Номера»</span></div><div><span className="stat-icon orange"><BarChart3 size={23}/></span><b>—</b><span>Метрики — в разделе «Статистика»</span></div></div>
 <Alert error={error}/><div className="section-top dashboard-section"><div><h2>Быстрый доступ</h2><p>Операции с каталогом и заказами</p></div></div>
 <div className="admin-actions-grid">{[['/admin/hotels',Hotel,'Отели','Создавать отели и изменять описание'],['/admin/rooms',BedDouble,'Номера','Управление категориями, ценами и удобствами'],['/admin/users',UsersRound,'Пользователи','Просмотр и управление учётными записями'],['/admin/bookings',CalendarDays,'Бронирования','Просмотр бронирований'],['/admin/statistics',ChartNoAxesColumn,'Статистика','Показатели по отелям']].map(([to,Icon,label,text])=><Link to={to} key={to} className="admin-action"><span>{createElement(Icon, { size: 22 })}</span><b>{label}</b><p>{text}</p><ArrowRight size={17} className="admin-action-arrow"/></Link>)}</div></>
}

export function AdminRooms(){
 const [hotelId,setHotelId]=useState('')
 const [hotel,setHotel]=useState(null)
 const [rooms,setRooms]=useState([])
 const [loading,setLoading]=useState(false)
 const [error,setError]=useState('')
 const [wizard,setWizard]=useState(null)
 const [page,setPage]=useState(0)
 const [pages,setPages]=useState(1)
 const [revision,setRevision]=useState(0)
 useEffect(()=>{
   let active=true
   const controller=new AbortController()
   setRooms([]);setError('')
   if(!hotelId)return ()=>{active=false;controller.abort()}
   setLoading(true)
   api.roomsPage({hotelId,page,size:20},{signal:controller.signal}).then(data=>{
     if(!active)return
     setRooms(arrayOf(data).map(normalRoom));setPages(Math.max(1,Number(data.totalPages||1)))
   }).catch(err=>{if(active&&err.name!=='AbortError')setError(`${err.message} Отображаемые данные могут быть устаревшими.`)})
     .finally(()=>{if(active)setLoading(false)})
   return()=>{active=false;controller.abort()}
 },[hotelId,page,revision])
 function refresh(){setRevision(v=>v+1)}
 return <>
   <PageTitle eyebrow="НОМЕРНОЙ ФОНД" title="Управление номерами" subtitle="Создавайте и изменяйте номера в мастере поверх страницы."
     action={<Button type="button" disabled={!hotelId} onClick={()=>setWizard({mode:'create'})}><Plus size={17}/> Добавить номер</Button>}/>
   <HotelPicker value={hotelId} onChange={(id,selected)=>{setHotelId(id);setHotel(selected);setPage(0);setWizard(null)}}/>
   <Alert error={error}/>
   {wizard&&createPortal(<RoomCreationWizard key={wizard.room?.id||'create'} hotelId={hotelId} hotelName={hotel?.name||`Отель #${hotelId}`}
     roomToEdit={wizard.room||null} initialStep={wizard.initialStep||0} onSaved={refresh} onClose={()=>{setWizard(null);refresh()}}/>,document.body)}
   {loading?<Spinner/>:<div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Номер</th><th>Категория</th><th>Гости</th><th>Цена за ночь</th><th>Удобства</th><th>Фото</th><th>Действия</th></tr></thead>
   <tbody>{rooms.map(room=><tr key={room.id}><td><b>№ {room.number}</b><small>ID {room.id}</small></td><td>{room.category}</td><td>{room.capacity}</td><td>{formatMoney(room.pricePerNight)}</td><td><Amenities items={room.amenities} limit={2}/></td><td className="room-admin-preview"><RoomPhotoImage retryable roomId={room.id} hotelId={hotelId} alt={`Номер ${room.number}`}/></td><td className="room-admin-controls"><Button type="button" size="small" variant="outline" onClick={()=>setWizard({mode:'edit',room,initialStep:0})}><Pencil size={15}/> Изменить</Button><Button type="button" size="small" variant="outline" onClick={()=>setWizard({mode:'edit',room,initialStep:1})}><ImagePlus size={15}/> Фотографии</Button></td></tr>)}</tbody></table>
   {!hotelId?<Empty title="Выберите отель"/>:rooms.length===0&&!error?<Empty title="Для этого отеля нет номеров" action={<Button type="button" onClick={()=>setWizard({mode:'create'})}>Создать номер</Button>}/>:null}
   <Pagination page={page} totalPages={pages} onChange={setPage}/></div>}
 </>
}

export function AdminBookings(){
 const [bookings,setBookings]=useState([])
 const [status,setStatus]=useState('ALL')
 const [error,setError]=useState('')
 const [loading,setLoading]=useState(true)
 const [page,setPage]=useState(0)
 const [pages,setPages]=useState(1)
 const [revision,setRevision]=useState(0)
 useEffect(()=>{
   let active=true;const controller=new AbortController();setLoading(true);setError('')
   api.bookingsAdmin({page,size:20,...(status==='ALL'?{}:{status})},{signal:controller.signal}).then(data=>{
     if(!active)return
     setBookings(arrayOf(data));setPages(Math.max(1,Number(data.totalPages||1)))
   }).catch(e=>{if(active&&e.name!=='AbortError')setError(`${e.message} Отображаемые данные могут быть устаревшими.`)})
     .finally(()=>{if(active)setLoading(false)})
   return()=>{active=false;controller.abort()}
 },[status,page,revision])
 return <><PageTitle eyebrow="ЗАКАЗЫ" title="Все бронирования" subtitle="Статусы бронирований в Booking Service."/>
 <div className="admin-filter"><label>Статус<select value={status} onChange={e=>{setStatus(e.target.value);setPage(0)}}><option value="ALL">Все</option>{['CONFIRMED','CANCELLED','PENDING','CANCELLING'].map(s=><option key={s} value={s}>{s}</option>)}</select></label><Button variant="outline" onClick={()=>setRevision(v=>v+1)}><RefreshCcw size={16}/> Обновить</Button></div>
 <Alert error={error}/>{loading?<Spinner/>:<div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Заказ</th><th>Пользователь</th><th>Номер</th><th>Заезд / выезд</th><th>Статус</th></tr></thead><tbody>{bookings.map(b=><tr key={b.id}><td><b>#{b.id}</b></td><td>{b.userEmail||b.userId||'—'}</td><td>#{b.roomId||'—'}</td><td>{formatDate(b.checkIn)} — {formatDate(b.checkOut)}</td><td><Status value={b.status}/></td></tr>)}</tbody></table>
 {bookings.length===0&&!error&&<Empty title="Бронирования отсутствуют"/>}
 <Pagination page={page} totalPages={pages} onChange={setPage}/></div>}</>
}

const STATS_LABELS={hotelId:'ID отеля',totalReservations:'Всего резерваций',reservedReservations:'Активные резервации',totalTimesBooked:'Количество бронирований номеров',tombstoneReservations:'Освобождённые резервации',totalRooms:'Всего номеров',availableRooms:'Доступных номеров',totalBookings:'Всего бронирований',confirmedBookings:'Подтверждённых бронирований',cancelledBookings:'Отменённых бронирований',occupancyRate:'Загрузка, %',timesBooked:'Количество броней'}
export function AdminStatistics(){
 const [hotelId,setHotelId]=useState('')
 const [stats,setStats]=useState(null)
 const [error,setError]=useState('')
 const [loading,setLoading]=useState(false)
 useEffect(()=>{
   let active=true;const controller=new AbortController();setStats(null);setError('')
   if(!hotelId)return()=>{active=false;controller.abort()}
   setLoading(true)
   api.statistics(hotelId,{signal:controller.signal}).then(data=>{if(active)setStats(data)})
     .catch(e=>{if(active&&e.name!=='AbortError'){setStats(null);setError(e.message)}})
     .finally(()=>{if(active)setLoading(false)})
   return()=>{active=false;controller.abort()}
 },[hotelId])
 const metrics=Object.entries(stats||{}).filter(([,v])=>typeof v==='number'||typeof v==='string'||typeof v==='boolean')
 return <><PageTitle eyebrow="АНАЛИТИКА" title="Статистика отелей" subtitle="Актуальные показатели Hotel Service, без имитации данных."/>
 <HotelPicker value={hotelId} onChange={id=>setHotelId(id)}/><Alert error={error}/>
 {loading?<Spinner/>:stats?<><div className="stat-cards">{metrics.map(([key,value],i)=><div key={key}><span className={`stat-icon ${['blue','green','orange'][i%3]}`}><BarChart3 size={22}/></span><b>{typeof value==='number'?value.toLocaleString('ru-RU'):String(value)}</b><span>{STATS_LABELS[key]||key.replace(/([A-Z])/g,' $1')}</span></div>)}</div>{!metrics.length&&<Empty title="Сервер не вернул числовую статистику"/>}</>:!error&&<Empty icon={ChartNoAxesColumn} title="Выберите отель для просмотра статистики"/>}</>
}
