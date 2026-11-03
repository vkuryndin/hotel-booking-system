import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ArrowLeft, ArrowRight, BedDouble, CalendarDays, CheckCircle2, ChevronRight, CircleUserRound, MapPin, Search, Shield, KeyRound, UserRound } from 'lucide-react'
import { api } from '../api/client.js'
import { cachedHotel, cachedRoom } from '../utils/detailCache.js'
import { useAuth } from '../context/AuthContext.jsx'
import { Alert, Button, Empty, ImageBlock, PageTitle, Spinner, Status, PasswordInput, Pagination } from '../components/Shared.jsx'
import { RoomGallery, RoomPhotoImage } from '../components/RoomPhotos.jsx'
import { HotelGallery } from '../components/HotelGallery.jsx'
import { arrayOf, formatDate, formatMoney, fullName, getHotelId, nights, normalRoom, plusDays, validDates } from '../utils/format.js'

export function AuthPage({mode='login'}){const {login,register,user,passwordChangeRequired}=useAuth();const navigate=useNavigate();const location=useLocation();const [form,setForm]=useState({email:'',password:'',username:'',firstName:'',lastName:''});const [error,setError]=useState('');const [saving,setSaving]=useState(false);const [passwordConfirmation,setPasswordConfirmation]=useState('')
 if(user) return passwordChangeRequired ? <Navigate to="/change-password" replace/> : <div className="container section-space"><div className="auth-panel"><h2>Вы уже вошли в аккаунт</h2><Link className="btn btn-primary" to="/account">Личный кабинет <ArrowRight size={17}/></Link></div></div>
 async function submit(e){e.preventDefault();setSaving(true);setError('');try{if(mode==='register' && (form.password.length>64 || new TextEncoder().encode(form.password).length>72))throw new Error('Пароль: до 64 символов и до 72 байт UTF-8.');if(mode==='register' && form.password!==passwordConfirmation)throw new Error('Пароль и подтверждение не совпадают.');let signedIn;if(mode==='register'){await register(form);signedIn=await login(form.email,form.password)}else signedIn=await login(form.email,form.password);if(signedIn?.passwordChangeRequired){navigate('/change-password', {replace:true});return}navigate(location.state?.from ? `${location.state.from.pathname}${location.state.from.search || ''}` : '/account', {replace:true})}catch(err){setError(err.message)}finally{setSaving(false)}}
 return <div className="auth-area"><div className="auth-image"><div><span className="eyebrow">HOTEL BOOKING</span><h2>Ваше следующее путешествие начинается здесь</h2><p>Исследуйте новые направления и управляйте поездками в одном месте.</p></div></div><div className="auth-form-side"><div className="auth-panel"><Link to="/" className="back-link"><ArrowLeft size={17}/> На главную</Link><span className="eyebrow">{mode==='login'?'С ВОЗВРАЩЕНИЕМ':'НАЧНИТЕ ПУТЕШЕСТВИЕ'}</span><h1>{mode==='login'?'Вход в аккаунт':'Создать аккаунт'}</h1><p className="muted">{mode==='login'?'Войдите, чтобы управлять своими бронированиями.':'Зарегистрируйтесь, чтобы бронировать отели.'}</p><Alert error={error}/><Alert success={location.state?.passwordChanged ? "Пароль изменён. Войдите с новым паролем." : ""}/><form onSubmit={submit} className="form-stack">{mode==='register'&&<><div className="form-row"><label>Имя<input required maxLength="80" value={form.firstName} onChange={e=>setForm({...form,firstName:e.target.value})} placeholder="Иван"/></label><label>Фамилия<input required maxLength="80" value={form.lastName} onChange={e=>setForm({...form,lastName:e.target.value})} placeholder="Петров"/></label></div><label>Имя пользователя<input required minLength="3" maxLength="30" pattern="[A-Za-z][A-Za-z0-9._-]{2,29}" title="3–30 символов, латиница, цифры, точка, дефис или подчёркивание" autoComplete="username" value={form.username} onChange={e=>setForm({...form,username:e.target.value})} placeholder="ivanpetrov"/></label></>}<label>Email<input type="email" required maxLength={255} autoComplete="email" value={form.email} onChange={e=>setForm({...form,email:e.target.value})} placeholder="you@example.com"/></label><label>Пароль<PasswordInput required minLength={mode==='register'?8:1} maxLength={mode==='register'?64:undefined} autoComplete={mode==='register'?'new-password':'current-password'} value={form.password} onChange={e=>setForm({...form,password:e.target.value})} placeholder="Введите пароль"/></label>{mode==='register'&&<label>Подтвердите пароль<PasswordInput required minLength={8} maxLength={64} autoComplete="new-password" value={passwordConfirmation} onChange={e=>setPasswordConfirmation(e.target.value)} placeholder="Повторите пароль"/></label>}<Button type="submit" disabled={saving}>{saving?'Подождите...':mode==='register'?'Зарегистрироваться':'Войти'} <ArrowRight size={17}/></Button></form><div className="auth-switch">{mode==='login'?<>Нет аккаунта? <Link to="/register">Зарегистрироваться</Link></>:<>Уже зарегистрированы? <Link to="/login">Войти</Link></>}</div></div></div></div>
}
/** Only screen allowed for an authenticated account that must replace its temporary password. */
export function ForcedPasswordChangePage() {
  const {user,loading,passwordChangeRequired,logout,login}=useAuth()
  const navigate=useNavigate()
  const [form,setForm]=useState({currentPassword:'',newPassword:'',confirmation:''})
  const [error,setError]=useState('')
  const [saving,setSaving]=useState(false)
  if(loading)return <Spinner/>
  if(!user)return <Navigate to="/login" replace/>
  if(!passwordChangeRequired)return <Navigate to="/account" replace/>
  async function submit(event){
    event.preventDefault();setError('')
    const {currentPassword,newPassword,confirmation}=form
    if(newPassword!==confirmation)return setError('Новые пароли не совпадают.')
    if(newPassword===currentPassword)return setError('Новый пароль должен отличаться от временного.')
    if(newPassword.length<8||newPassword.length>64||new TextEncoder().encode(newPassword).length>72)return setError('Новый пароль: 8–64 символа, не более 72 байт UTF-8.')
    setSaving(true)
    try{
      const accountEmail = user.email
      await api.changeMyPassword({currentPassword,newPassword})
      setForm({currentPassword:'',newPassword:'',confirmation:''})
      // The old JWT is revoked. Clear it before requesting a fresh token.
      logout()
      try {
        const signedIn = await login(accountEmail,newPassword)
        if (signedIn.passwordChangeRequired) throw new Error('Сервер снова запросил смену пароля.')
        navigate('/account',{replace:true})
      } catch {
        logout()
        navigate('/login',{replace:true,state:{passwordChanged:true}})
      }
    }catch(err){setError(err.message)}finally{setSaving(false)}
  }
  return <div className="container forced-password-page"><div className="profile-card forced-password-card">
    <div className="profile-password-heading"><KeyRound size={26}/><div><h1>Требуется сменить пароль</h1><p>Администратор сбросил пароль вашей учётной записи. Для продолжения установите новый пароль.</p></div></div>
    <div className="forced-account-identity"><CircleUserRound size={19}/><div><span>Вы вошли в учётную запись</span><strong>{user.email || `ID #${user.id}`}</strong></div></div>
    <div className="admin-users-help"><Shield size={18}/> Используйте временный пароль, с которым только что вошли. После смены пароля система попробует войти автоматически. Если это не удастся, войдите вручную с новым паролем.</div>
    <Alert error={error}/>
    <form className="form-stack" onSubmit={submit}>
      <label>Текущий (временный) пароль<PasswordInput required autoComplete="current-password" value={form.currentPassword} onChange={e=>setForm(p=>({...p,currentPassword:e.target.value}))}/></label>
      <label>Новый пароль<PasswordInput required minLength={8} maxLength={64} autoComplete="new-password" value={form.newPassword} onChange={e=>setForm(p=>({...p,newPassword:e.target.value}))}/></label>
      <label>Подтверждение нового пароля<PasswordInput required minLength={8} maxLength={64} autoComplete="new-password" value={form.confirmation} onChange={e=>setForm(p=>({...p,confirmation:e.target.value}))}/></label>
      <p className="muted smaller">8–64 символа, максимум 72 байта UTF-8. Старый и новый пароли должны отличаться.</p>
      <Button disabled={saving} type="submit">{saving?'Сохраняем...':'Сменить пароль и продолжить'} <ArrowRight size={17}/></Button>
    </form>
    <button className="link-button forced-logout" type="button" onClick={()=>{logout();navigate('/login',{replace:true})}}>Выйти из аккаунта</button>
  </div></div>
}
export function BookingPage(){const {hotelId}=useParams();const [params]=useSearchParams();const {user}=useAuth();const roomId=params.get('roomId');const autoSelect=!roomId;const [checkIn,setCheckIn]=useState(params.get('checkIn')||plusDays(1));const [checkOut,setCheckOut]=useState(params.get('checkOut')||plusDays(3));const [guests,setGuests]=useState(Number(params.get('guests')||2));const [hotel,setHotel]=useState(null);const [room,setRoom]=useState(null);const [error,setError]=useState('');const [saving,setSaving]=useState(false);const [result,setResult]=useState(null);const [key,setKey]=useState(()=>crypto.randomUUID());const [ready,setReady]=useState(false)
 useEffect(()=>{let alive=true;Promise.allSettled([api.hotel(hotelId),roomId?api.room(roomId):Promise.resolve(null)]).then(([h,r])=>{if(!alive)return;if(h.status==='fulfilled')setHotel(h.value);else setError(h.reason.message);if(r.status==='fulfilled' && r.value){const found=normalRoom(r.value);if(String(found.id)===String(roomId)&&String(getHotelId(found))===String(hotelId))setRoom(found);else setError('Выбранный номер не принадлежит этому отелю.')}else if(roomId&&r.status==='rejected')setError(r.reason.message)}).finally(()=>{if(alive)setReady(true)});return()=>{alive=false}},[hotelId,roomId])
 function changeData(fn,value){fn(value);setKey(crypto.randomUUID());setError('')}
 async function submit(e){e.preventDefault();if(!validDates(checkIn,checkOut)){setError('Выберите корректные даты (1–365 ночей).');return}if(!autoSelect&&!room){setError('Не удалось подтвердить выбранный номер. Вернитесь к поиску.');return}setSaving(true);setError('');try{const data={checkIn,checkOut,guestCount:guests,...(autoSelect?{hotelId:Number(hotelId),autoSelect:true}:{roomId:Number(roomId)})};const booking=await api.createBooking(data,key);setResult(booking)}catch(err){setError(err.message);if(err.code!=='REQUEST_IN_PROGRESS'&&err.code!=='IDEMPOTENCY_KEY_CONFLICT'){/* keep key to safely retry same request */}}finally{setSaving(false)}}
 if(!ready)return <div className="container"><Spinner/></div>
 return <div className="container checkout-page"><div className="breadcrumbs"><Link to="/hotels">Каталог</Link><ChevronRight size={14}/><Link to={`/hotels/${hotelId}`}>{hotel?.name||'Отель'}</Link><ChevronRight size={14}/>Бронирование</div>{result?<div className="booking-success"><div className="success-icon"><CheckCircle2 size={48}/></div><h1>Бронирование подтверждено!</h1><p>Ваше бронирование #{result.id} успешно создано. Все детали доступны в личном кабинете.</p><div className="success-card"><span>Номер бронирования</span><b>#{result.id}</b><span>Статус</span><Status value={result.status}/><span>Номер комнаты</span><b>#{result.roomId||roomId||'назначен автоматически'}</b></div><div className="button-row"><Link className="btn btn-primary" to={`/account/bookings/${result.id}`}>Детали бронирования <ArrowRight size={17}/></Link><Link className="btn btn-outline" to="/account/bookings">Мои бронирования <ArrowRight size={17}/></Link><Link className="btn btn-outline" to="/hotels">Продолжить поиск</Link></div></div>:<><PageTitle eyebrow="ПОЧТИ ГОТОВО" title="Оформление бронирования" subtitle="Проверьте детали поездки перед подтверждением."/><div className="checkout-steps"><span className="active">1 · Выбор номера</span><span className="active">2 · Детали поездки</span><span>3 · Подтверждение</span></div><div className="checkout-layout"><form className="checkout-main" onSubmit={submit}><div className="white-card"><h2>Ваш отель</h2><div className="selected-hotel">{room&&!autoSelect ? <RoomPhotoImage fallbackToHotel roomId={room.id} hotelId={hotelId} fallback={2}/> : <ImageBlock hotelId={hotelId} fallback={2}/>}<div><h3>{hotel?.name||`Отель #${hotelId}`}</h3><p><MapPin size={15}/>{hotel?.address||hotel?.city||'Адрес не указан'}</p><p>{room?`Номер ${room.number} · ${room.category}`:'Автоматический подбор номера'}</p></div></div></div><div className="white-card"><h2>Даты и гости</h2><div className="form-row"><label>Дата заезда<input type="date" min={plusDays(0)} value={checkIn} onChange={e=>changeData(setCheckIn,e.target.value)}/></label><label>Дата выезда<input type="date" min={checkIn} value={checkOut} onChange={e=>changeData(setCheckOut,e.target.value)}/></label></div><label>Количество гостей<select value={guests} onChange={e=>changeData(setGuests,Number(e.target.value))}>{Array.from({length:12},(_,i)=><option key={i+1} value={i+1}>{i+1}</option>)}</select></label></div><div className="white-card checkout-room-choice">
   {roomId ? <>
     <div className="checkout-room-choice-heading"><h2>Выбранный номер</h2><Link className="btn btn-outline btn-small" to={`/hotels/${hotelId}?${new URLSearchParams({checkIn,checkOut,guests:String(guests)})}#rooms`}>Изменить номер <ArrowRight size={15}/></Link></div>
     <p className="checkout-selected-room"><CheckCircle2 size={18}/> Номер {room?.number || roomId}{room?.category ? ` · ${room.category}` : ''} выбран на предыдущем шаге.</p>
     {room && <div className="checkout-room-gallery"><h3>Фотографии выбранного номера</h3><RoomGallery roomId={room.id} hotelId={hotelId} number={room.number}/></div>}
     <p className="muted smaller">При подтверждении брони используется именно этот номер. Автоматический подбор не включается.</p>
   </> : <>
     <h2>Автоматический подбор номера</h2>
     <p className="checkout-selected-room"><CheckCircle2 size={18}/> Система выберет свободный номер.</p>
     <p className="muted smaller">Стратегию подбора задаёт администратор отеля.</p>
     <Link className="text-link" to={`/hotels/${hotelId}?${new URLSearchParams({checkIn,checkOut,guests:String(guests)})}#rooms`}>Выбрать номер вручную <ArrowRight size={15}/></Link>
   </>}
 </div><div className="white-card"><h2>Данные гостя</h2><p className="muted">Бронирование будет привязано к вашему аккаунту.</p><div className="guest-details"><CircleUserRound size={22}/><div><b>{fullName(user)}</b><span>{user?.email}</span></div></div></div><Alert error={error}/><Button disabled={saving || (!autoSelect && !room) || !hotel} className="confirm-booking" type="submit">{saving?'Подтверждаем бронирование...':'Подтвердить бронирование'} <ArrowRight size={18}/></Button><p className="muted smaller"><Shield size={15}/> Оплата не списывается. Бронирование создаётся через Booking Service.</p></form><aside className="checkout-summary"><h3>Ваш заказ</h3>{room&&!autoSelect ? <RoomPhotoImage fallbackToHotel roomId={room.id} hotelId={hotelId} fallback={1}/> : <ImageBlock hotelId={hotelId} fallback={1}/>}<h4>{hotel?.name}</h4><div className="summary-line"><span>Заезд</span><b>{checkIn}</b></div><div className="summary-line"><span>Выезд</span><b>{checkOut}</b></div><div className="summary-line"><span>Продолжительность</span><b>{nights(checkIn,checkOut)} ноч.</b></div><div className="summary-line"><span>Гости</span><b>{guests}</b></div>{room&&!autoSelect?<><div className="summary-line"><span>За ночь</span><b>{formatMoney(room.pricePerNight)}</b></div><div className="summary-total"><span>Ориентировочно</span><strong>{formatMoney(Number(room.pricePerNight)*nights(checkIn,checkOut))}</strong></div></>:<p className="summary-hint">Стоимость станет известна после выбора номера. Точная сумма не рассчитывается backend на этом этапе.</p>}<div className="widget-note"><CheckCircle2 size={16}/> Без реального списания средств</div></aside></div></>}</div>
}
export function AccountOverview(){
 const {user}=useAuth()
 const [list,setList]=useState([])
 const [total,setTotal]=useState(0)
 const [cancelled,setCancelled]=useState(0)
 const [upcomingCount,setUpcomingCount]=useState(0)
 const [loading,setLoading]=useState(true)
 const [error,setError]=useState('')
 useEffect(()=>{
  let active=true
  Promise.all([api.bookingsMy({page:0,size:3,status:'CONFIRMED',checkOutFrom:plusDays(0)}),api.bookingsMy({page:0,size:1}),api.bookingsMy({page:0,size:1,status:'CANCELLED'})]).then(([confirmed,all,cancelledResult])=>{
    if(!active)return
    setList(arrayOf(confirmed))
    setUpcomingCount(Number(confirmed.totalElements||0))
    setTotal(Number(all.totalElements||0))
    setCancelled(Number(cancelledResult.totalElements||0))
  }).catch(e=>{if(active)setError(e.message)}).finally(()=>{if(active)setLoading(false)})
  return()=>{active=false}
 },[])
 const upcoming=list
 return <><PageTitle eyebrow="ЛИЧНЫЙ КАБИНЕТ" title={`Здравствуйте, ${user?.firstName||fullName(user)}!`} subtitle="Здесь вся информация о ваших поездках."/><div className="stat-cards"><div><span className="stat-icon blue"><CalendarDays size={22}/></span><b>{upcomingCount}</b><span>Предстоящие поездки</span></div><div><span className="stat-icon green"><CheckCircle2 size={22}/></span><b>{total}</b><span>Всего бронирований</span></div><div><span className="stat-icon orange"><BedDouble size={22}/></span><b>{cancelled}</b><span>Отменено</span></div></div><div className="section-top dashboard-section"><div><h2>Ближайшие поездки</h2><p>Брони, которые ожидают вас.</p></div><Link className="text-link" to="/account/bookings">Все бронирования <ArrowRight size={17}/></Link></div><Alert error={error}/>{loading?<Spinner/>:upcoming.length>0?<div className="booking-list">{upcoming.slice(0,3).map(b=><BookingItem key={b.id} booking={b}/>)}</div>:<Empty title="Активных поездок нет" text="Найдите подходящий отель и забронируйте номер." action={<Link className="btn btn-primary" to="/hotels">Найти отель <Search size={17}/></Link>}/>}</>
}
export function BookingItem({booking,onCancel,cancelling=false}) {
  const initialHotelId = booking.hotelId ?? booking.hotel?.id ?? getHotelId(booking.room)
  const [hotelId,setHotelId] = useState(initialHotelId ?? null)
  const [hotel,setHotel] = useState(null)
  const [room,setRoom] = useState(booking.room ?? null)

  useEffect(()=>{
    let alive=true
    async function loadBookingDetails(){
      let resolvedHotelId = booking.hotelId ?? booking.hotel?.id ?? getHotelId(booking.room)
      // Booking DTO may only contain roomId; look up its hotel without changing the backend.
      if (booking.roomId) {
        try {
          const foundRoom = await cachedRoom(booking.roomId)
          if (!alive) return
          setRoom(foundRoom)
          resolvedHotelId = resolvedHotelId ?? getHotelId(foundRoom)
        } catch { /* Keep the booking visible even when room details are unavailable. */ }
      }
      if (!alive) return
      setHotelId(resolvedHotelId ?? null)
      if (resolvedHotelId) {
        try {
          const foundHotel = await cachedHotel(resolvedHotelId)
          if (alive) setHotel(foundHotel)
        } catch { /* Photo and name can fall back if the hotel is unavailable. */ }
      }
    }
    loadBookingDetails()
    return ()=>{alive=false}
  },[booking.hotelId,booking.hotel?.id,booking.roomId,booking.room])

  const canCancel=booking.status==='CONFIRMED'
  return <div className="booking-item">
    <Link to={`/account/bookings/${booking.id}`} className="booking-item-pic" aria-label={`Открыть бронирование #${booking.id}`}><RoomPhotoImage fallbackToHotel roomId={booking.roomId || room?.id} hotelId={hotelId} fallback={booking.id||0} alt={room?.number ? `Номер ${room.number}` : hotel?.name || 'Фотография отеля'}/></Link>
    <div className="booking-item-body">
      <div className="booking-item-title"><h3><Link to={`/account/bookings/${booking.id}`}>{hotel?.name||booking.hotelName||`Бронирование #${booking.id}`}</Link></h3><Status value={booking.status}/></div>
      <p><CalendarDays size={15}/>{formatDate(booking.checkIn)} — {formatDate(booking.checkOut)}</p>
      <p><BedDouble size={15}/>Номер {room?.number||booking.roomNumber||booking.roomId||'—'} · {booking.guestCount||1} гост.</p>
      <small>Заказ #{booking.id}</small>
    </div>
    <div className="booking-item-actions">
      <Link className="btn btn-outline btn-small" to={`/account/bookings/${booking.id}`}>Детали <ArrowRight size={15}/></Link>
      {booking.totalPrice!=null&&<strong>{formatMoney(booking.totalPrice)}</strong>}
      {canCancel&&onCancel&&<Button variant="danger-light" size="small" disabled={cancelling} onClick={()=>onCancel(booking)}>{cancelling?'Отменяем...':'Отменить'}</Button>}
      {hotelId&&<Link className="tiny-link" to={`/hotels/${hotelId}`}>Повторить поездку <ArrowRight size={14}/></Link>}
    </div>
  </div>
}
export function AccountBookings(){
 const [list,setList]=useState([])
 const [status,setStatus]=useState('ALL')
 const [bookingPage,setBookingPage]=useState(0)
 const [totalPages,setTotalPages]=useState(1)
 const [counts,setCounts]=useState({ALL:0,CONFIRMED:0,CANCELLED:0})
 const [loading,setLoading]=useState(true)
 const [error,setError]=useState('')
 const [success,setSuccess]=useState('')
 const [cancelling,setCancelling]=useState(null)
 const [revision,setRevision]=useState(0)
 useEffect(()=>{
   let active=true
   Promise.all(['ALL','CONFIRMED','CANCELLED'].map(s=>api.bookingsMy({page:0,size:1,...(s==='ALL'?{}:{status:s})}))).then(results=>{
     if(active)setCounts(Object.fromEntries(results.map((r,i)=>[['ALL','CONFIRMED','CANCELLED'][i],Number(r.totalElements||0)])))
   }).catch(e=>{if(active)setError(e.message)})
   return()=>{active=false}
 },[revision])
 useEffect(()=>{
   let active=true
   setLoading(true);setError('')
   api.bookingsMy({page:bookingPage,size:10,...(status==='ALL'?{}:{status})}).then(data=>{
     if(!active)return
     setList(arrayOf(data));setTotalPages(Math.max(1,Number(data.totalPages||1)))
     if(bookingPage>=Math.max(1,Number(data.totalPages||1)))setBookingPage(0)
   }).catch(e=>{if(active){setList([]);setTotalPages(1);setError(e.message)}})
     .finally(()=>{if(active)setLoading(false)})
   return()=>{active=false}
 },[status,bookingPage,revision])
 async function cancel(booking){
   if(!window.confirm(`Отменить бронирование #${booking.id}?`))return
   setCancelling(booking.id);setError('');setSuccess('')
   try{await api.cancelBooking(booking.id);setSuccess('Бронирование отменено.');setRevision(n=>n+1)}
   catch(e){setError(e.message)}finally{setCancelling(null)}
 }
 const tabs=[['ALL','Все'],['CONFIRMED','Подтверждённые'],['CANCELLED','Отменённые']]
 return <><PageTitle eyebrow="ИСТОРИЯ ПОЕЗДОК" title="Мои бронирования" subtitle="Все ваши поездки и их актуальный статус." action={<Link to="/hotels" className="btn btn-primary btn-small">Найти отель <ArrowRight size={16}/></Link>}/><div className="tab-row">{tabs.map(([value,label])=><button key={value} className={status===value?'active':''} onClick={()=>{setStatus(value);setBookingPage(0)}}>{label} <span>({counts[value]})</span></button>)}</div><Alert error={error} success={success}/>{loading?<Spinner/>:list.length?<div className="booking-list">{list.map(b=><BookingItem key={b.id} booking={b} onCancel={cancel} cancelling={cancelling===b.id}/>)}</div>:<Empty title={status==='CONFIRMED'?'Подтверждённых бронирований нет':status==='CANCELLED'?'Отменённых бронирований нет':'Бронирований пока нет'} text="Выберите отель, чтобы забронировать номер." action={<Link to="/hotels" className="btn btn-primary">Выбрать отель</Link>}/>} {!loading&&<Pagination page={bookingPage} totalPages={totalPages} onChange={setBookingPage}/>}</>
}

/** Booking Service enforces ownership on GET /api/bookings/{id}. */
export function BookingDetailsPage() {
  const {id} = useParams()
  const [booking,setBooking] = useState(null)
  const [room,setRoom] = useState(null)
  const [hotel,setHotel] = useState(null)
  const [photos,setPhotos] = useState([])
  const [photoError,setPhotoError] = useState('')
  const [loading,setLoading] = useState(true)
  const [error,setError] = useState('')
  const [success,setSuccess] = useState('')
  const [cancelling,setCancelling] = useState(false)
  const [revision,setRevision] = useState(0)

  useEffect(() => {
    let active = true
    setLoading(true);setError('');setBooking(null);setRoom(null);setHotel(null);setPhotos([]);setPhotoError('')
    async function load() {
      try {
        if (!/^\d+$/.test(String(id))) throw Error('Некорректный номер бронирования.')
        const data=await api.booking(id)
        if (!active) return
        setBooking(data)
        if (!data.roomId) return
        try {
          const foundRoom=normalRoom(await cachedRoom(data.roomId))
          if (!active) return
          setRoom(foundRoom)
          const hotelId=getHotelId(foundRoom)
          if (hotelId) {
            const [hotelResult,photosResult]=await Promise.allSettled([cachedHotel(hotelId),api.photos(hotelId)])
            if (!active) return
            if (hotelResult.status==='fulfilled') setHotel(hotelResult.value)
            if (photosResult.status==='fulfilled') setPhotos(arrayOf(photosResult.value))
            else setPhotoError(photosResult.reason?.message || 'Фотографии отеля недоступны.')
          }
        } catch {
          // A deleted/unavailable room must not prevent opening its booking details.
        }
      } catch(err) { if(active) setError(err.message) }
      finally {if(active) setLoading(false)}
    }
    load()
    return () => {active=false}
  },[id,revision])

  async function cancelBooking() {
    if (!window.confirm(`Отменить бронирование #${id}?`)) return
    setCancelling(true);setError('');setSuccess('')
    try {await api.cancelBooking(id);setSuccess('Бронирование отменено.');setRevision(n=>n+1)}
    catch(err){setError(err.message)}
    finally{setCancelling(false)}
  }

  const hotelId=getHotelId(room)
  return <div className="booking-detail-page">
    <div className="breadcrumbs"><Link to="/account/bookings"><ArrowLeft size={15}/> Мои бронирования</Link><ChevronRight size={14}/> Бронирование #{id}</div>
    <PageTitle eyebrow="ДЕТАЛИ ПОЕЗДКИ" title={`Бронирование #${id}`} subtitle="Данные заказа и фотографии отеля и номера."/>
    {success && <Alert success={success}/>} {error && <Alert error={error}/>} 
    {loading ? <Spinner/> : booking ? <>
      <section className="white-card booking-detail-summary">
        <div className="booking-detail-heading"><div><h2>Информация о бронировании</h2><p className="muted">Заказ #{booking.id}</p></div><Status value={booking.status}/></div>
        <dl className="booking-detail-facts">
          <div><dt>Заезд</dt><dd>{formatDate(booking.checkIn)}</dd></div>
          <div><dt>Выезд</dt><dd>{formatDate(booking.checkOut)}</dd></div>
          <div><dt>Продолжительность</dt><dd>{nights(booking.checkIn,booking.checkOut)} ноч.</dd></div>
          <div><dt>Количество гостей</dt><dd>{booking.guestCount}</dd></div>
          <div><dt>Номер</dt><dd>{room ? `${room.number} · ${room.category}` : `ID #${booking.roomId || '—'}`}</dd></div>
          <div><dt>Дата создания</dt><dd>{booking.createdAt ? new Date(booking.createdAt).toLocaleString('ru-RU') : '—'}</dd></div>
        </dl>
        {room?.pricePerNight!=null && <p className="muted smaller">Текущая цена номера: {formatMoney(room.pricePerNight)} за ночь. Итоговую стоимость исходного заказа Booking Service не возвращает.</p>}
        {booking.status==='CONFIRMED' && <Button type="button" variant="danger-light" disabled={cancelling} onClick={cancelBooking}>{cancelling?'Отменяем...':'Отменить бронирование'}</Button>}
      </section>
      <div className="booking-detail-galleries">
        <section className="white-card booking-detail-gallery-section">
          <h2>Отель {hotel?.name || (hotelId ? `#${hotelId}` : '')}</h2>
          {hotel?.address && <p className="muted"><MapPin size={16}/> {hotel.address}</p>}
          {hotelId ? <HotelGallery hotelId={hotelId} hotelName={hotel?.name || `Отель #${hotelId}`} photos={photos} error={photoError}/> : <p className="muted">Не удалось определить отель по номеру. Сведения о бронировании доступны выше.</p>}
          {hotelId && <Link className="text-link" to={`/hotels/${hotelId}`}>Открыть страницу отеля <ArrowRight size={16}/></Link>}
        </section>
        <section className="white-card booking-detail-gallery-section">
          <h2>Номер {room?.number || booking.roomId || '—'}</h2>
          {room?.category && <p className="muted">Категория: {room.category} · Вместимость: {room.capacity ?? '—'}</p>}
          {booking.roomId ? <RoomGallery roomId={booking.roomId} hotelId={hotelId} number={room?.number || booking.roomId}/> : <p className="muted">Номер пока не определён.</p>}
        </section>
      </div>
    </> : !error && <Empty title="Бронирование не найдено" text="Проверьте номер заказа или вернитесь к истории."/>}
  </div>
}

export function AccountProfile() {
  const { user, refreshUser, logout, login } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({firstName:'',lastName:'',username:'',phone:''})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [passwords, setPasswords] = useState({currentPassword:'',newPassword:'',confirmation:''})
  const [passwordSaving, setPasswordSaving] = useState(false)
  const [passwordError, setPasswordError] = useState('')

  useEffect(() => {
    api.me()
      .then(data => setForm({firstName:data.firstName||'',lastName:data.lastName||'',username:data.username||'',phone:data.phone||''}))
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  async function submit(e) {
    e.preventDefault()
    setSaving(true)
    setError('')
    setSuccess('')
    try {
      await api.editMe(form)
      await refreshUser()
      setSuccess('Данные профиля обновлены.')
    } catch(e) { setError(e.message) }
    finally { setSaving(false) }
  }

  async function changePassword(e) {
    e.preventDefault()
    setPasswordError('')
    const {currentPassword,newPassword,confirmation} = passwords
    if (!currentPassword) return setPasswordError('Укажите текущий пароль.')
    if (newPassword.length < 8 || newPassword.length > 64 || new TextEncoder().encode(newPassword).length > 72) {
      return setPasswordError('Новый пароль: от 8 до 64 символов, не более 72 байт UTF-8.')
    }
    if (newPassword !== confirmation) return setPasswordError('Новые пароли не совпадают.')
    if (currentPassword === newPassword) return setPasswordError('Новый пароль должен отличаться от текущего.')
    setPasswordSaving(true)
    try {
      await api.changeMyPassword({currentPassword,newPassword})
      setPasswords({currentPassword:'',newPassword:'',confirmation:''})
      // Invalidate the old token and obtain a fresh JWT with the new password.
      const email = user.email
      logout()
      try {
        const signedIn = await login(email,newPassword)
        if (signedIn.passwordChangeRequired) throw new Error('Повторно требуется смена пароля.')
        navigate('/account/profile', {replace:true})
      } catch {
        logout()
        navigate('/login', {replace:true, state:{passwordChanged:true}})
      }
    } catch(e) { setPasswordError(e.message) }
    finally { setPasswordSaving(false) }
  }

  return <>
    <PageTitle eyebrow="ЛИЧНЫЕ ДАННЫЕ" title="Мой профиль" subtitle="Измените информацию, которая отображается в вашем аккаунте."/>
    <div className="profile-card">
      <div className="profile-top"><div className="large-avatar"><UserRound size={44}/></div><div><h2>{fullName(user)}</h2><span>{user?.email}</span></div></div>
      {loading ? <Spinner/> : <form className="form-stack" onSubmit={submit}>
        <Alert error={error} success={success}/>
        <div className="form-row">
          <label>Имя<input required maxLength={80} value={form.firstName} onChange={e=>setForm({...form,firstName:e.target.value})}/></label>
          <label>Фамилия<input required maxLength={80} value={form.lastName} onChange={e=>setForm({...form,lastName:e.target.value})}/></label>
        </div>
        <label>Имя пользователя<input required minLength={3} maxLength={30} pattern="[A-Za-z][A-Za-z0-9._-]{2,29}" value={form.username} onChange={e=>setForm({...form,username:e.target.value})}/></label>
        <label>Телефон<input type="tel" pattern="\+[1-9][0-9]{7,14}" title="Номер в международном формате: + и 8–15 цифр" value={form.phone} onChange={e=>setForm({...form,phone:e.target.value})} placeholder="+374 ..."/></label>
        <label>Email <span className="muted-inline">(используется для входа)</span><input value={user?.email||''} disabled/></label>
        <div className="profile-actions"><Button type="submit" disabled={saving}>{saving?'Сохраняем...':'Сохранить изменения'}</Button></div>
      </form>}
    </div>
    <section className="profile-card profile-password-card" aria-labelledby="change-password-title">
      <div className="profile-password-heading"><KeyRound size={24}/><div><h2 id="change-password-title">Смена пароля</h2><p>Доступна для всех пользователей, включая администратора.</p></div></div>
      <form className="form-stack" onSubmit={changePassword}>
        <Alert error={passwordError}/>
        <label>Текущий пароль<PasswordInput required autoComplete="current-password" value={passwords.currentPassword} onChange={e=>setPasswords(p=>({...p,currentPassword:e.target.value}))}/></label>
        <div className="form-row">
          <label>Новый пароль<PasswordInput required minLength={8} maxLength={64} autoComplete="new-password" value={passwords.newPassword} onChange={e=>setPasswords(p=>({...p,newPassword:e.target.value}))}/></label>
          <label>Повторите новый пароль<PasswordInput required minLength={8} maxLength={64} autoComplete="new-password" value={passwords.confirmation} onChange={e=>setPasswords(p=>({...p,confirmation:e.target.value}))}/></label>
        </div>
        <p className="profile-password-note">8–64 символа, не более 72 байт UTF-8. После смены пароля система попробует войти автоматически. Если это не удастся, войдите с новым паролем.</p>
        <div className="profile-actions"><Button type="submit" disabled={passwordSaving}>{passwordSaving?'Меняем пароль...':'Сменить пароль'}</Button></div>
      </form>
    </section>
    <div className="profile-security"><Shield size={21}/><div><b>Безопасность аккаунта</b><p>Вход защищён JWT. При смене пароля прежние токены перестают действовать.</p></div></div>
  </>
}
