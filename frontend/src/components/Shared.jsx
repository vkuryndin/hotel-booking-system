import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, Navigate } from 'react-router-dom'
import { UsersRound, BedDouble, CalendarDays, ChartNoAxesColumn, ChevronRight, CircleUserRound, CreditCard, Hotel, ImagePlus, LayoutDashboard, LogOut, MapPin, Menu, Search, Settings, Shield, Star, UserRound, X, Wifi, CircleAlert, LoaderCircle, ArrowRight, Home, CheckCircle2, SlidersHorizontal, ImageOff, Eye, EyeOff } from 'lucide-react'
import { api, imageBlob } from '../api/client.js'
import { useAuth } from '../context/AuthContext.jsx'
import { amenity, formatMoney, fullName, statusName, statusTone } from '../utils/format.js'
export { BedDouble, CalendarDays, ChartNoAxesColumn, ChevronRight, CircleUserRound, CreditCard, Hotel, ImagePlus, LayoutDashboard, LogOut, MapPin, Menu, Search, Settings, Shield, Star, UserRound, X, Wifi, CircleAlert, LoaderCircle, ArrowRight, Home, CheckCircle2, SlidersHorizontal, ImageOff }

export function Button({children,variant='primary',size='',className='',type='button',...props}) {return <button type={type} className={`btn btn-${variant} ${size ? `btn-${size}`:''} ${className}`} {...props}>{children}</button>}
/** Consistent, always-visible password visibility toggle (no browser-native reveal icon). */
export function PasswordInput({className='', ...props}) {
  const [visible, setVisible] = useState(false)
  const title = visible ? 'Скрыть пароль' : 'Показать пароль'
  return <span className={`password-input-wrap ${className}`}
    onBlurCapture={(event) => {
      if (!event.currentTarget.contains(event.relatedTarget)) setVisible(false)
    }}>
    <input {...props} type={visible ? 'text' : 'password'}/>
    <button type="button" className="password-visibility-toggle"
      title={title} aria-label={title} aria-pressed={visible}
      onClick={(event) => { event.preventDefault(); setVisible(value => !value) }}>
      {visible ? <EyeOff size={19} aria-hidden="true"/> : <Eye size={19} aria-hidden="true"/>}
    </button>
  </span>
}
export function Alert({error,success,onClose}) {const value=error || success;if(!value) return null;return <div role="alert" className={`alert ${error?'alert-error':'alert-success'}`}><span>{error?<CircleAlert size={18}/>:<CheckCircle2 size={18}/>}</span><span>{typeof value==='string'?value:value?.message||'Ошибка'}</span>{onClose&&<button aria-label="Закрыть" onClick={onClose}><X size={16}/></button>}</div>}
export function Spinner({label='Загружаем данные...'}) {return <div className="spinner" role="status"><LoaderCircle size={27} className="spin"/><span>{label}</span></div>}
export function Empty({icon:Icon=BedDouble,title='Ничего не найдено',text,action}) {return <div className="empty"><div className="empty-icon"><Icon size={28}/></div><h3>{title}</h3>{text&&<p>{text}</p>}{action}</div>}
export function Stars({rating}) {if(rating===undefined || rating===null) return null;return <span className="stars"><Star size={15} fill="currentColor"/> <b>{Number(rating).toFixed(1)}</b></span>}
export function Status({value}) {return <span className={`status status-${statusTone(value)}`}>{statusName(value)}</span>}
export function Amenities({items=[],limit=5}) {return <div className="amenities">{(Array.isArray(items)?items:[]).slice(0,limit).map(i=><span key={i}><CheckCircle2 size={14}/>{amenity(i)}</span>)}</div>}
export function HotelNoPhotos({message='Нет фотографий отеля',className=''}) {
  return <div className={`hotel-no-photos ${className}`} role="img" aria-label={message}>
    <ImageOff size={34} strokeWidth={1.6} aria-hidden="true"/><span>{message}</span>
  </div>
}
export function ImageBlock({hotelId,photoId,alt='',className='',fallback=0}) {
  const [url,setUrl]=useState(null)
  const [state,setState]=useState('loading')
  useEffect(()=>{
    let alive=true
    let objectUrl
    const controller=new AbortController()
    setUrl(null)
    setState(hotelId ? 'loading' : 'empty')
    if (!hotelId) return () => controller.abort()
    async function load(){
      try {
        let id=photoId
        if(!id){
          const list=await api.photos(hotelId)
          if(!alive)return
          const photos=Array.isArray(list)?list:[]
          id=(photos.find(p=>p.primary)||photos[0])?.id
        }
        if(!id){ if(alive)setState('empty'); return }
        const blob=await imageBlob(`/hotels/${hotelId}/photos/${id}/file`,controller.signal)
        if(!alive)return
        objectUrl=URL.createObjectURL(blob)
        setUrl(objectUrl)
        setState('ready')
      } catch (err) {
        if(alive && err.name!=='AbortError')setState('error')
      }
    }
    load()
    return ()=>{alive=false;controller.abort();if(objectUrl)URL.revokeObjectURL(objectUrl)}
  },[hotelId,photoId])
  return <div className={`photo-frame photo-${fallback%5} ${className}`}>
    {state==='ready'&&url ? <img src={url} alt={alt||'Фотография отеля'} loading="lazy"/>
      : state==='loading' ? <div className="hotel-no-photos hotel-photo-loading" role="status"><LoaderCircle size={24} className="spin"/><span>Загружаем фото…</span></div>
      : <HotelNoPhotos message={state==='error'?'Не удалось загрузить фото отеля':'Нет фотографий отеля'}/>}
  </div>
}
/** Shared numeric pagination; page and callback use zero-based indexes. */
export function Pagination({page=0,totalPages=1,onChange,className=''}) {
  const max=Math.max(1,Math.floor(Number(totalPages)||1))
  const current=Math.min(Math.max(0,Number(page)||0),max-1)
  const [jump,setJump]=useState(String(current+1))
  useEffect(()=>setJump(String(current+1)),[current])
  if(max<=1)return null
  const from=Math.max(0,Math.min(max-5,current-2))
  const pages=Array.from({length:Math.min(5,max)},(_,i)=>from+i)
  function go(i) {if(i!==current&&i>=0&&i<max)onChange(i)}
  function submit(e){e.preventDefault();if(!/^\d+$/.test(jump.trim()))return;const target=Number(jump)-1;if(Number.isSafeInteger(target))go(target)}
  return <nav className={`numbered-pagination ${className}`} aria-label="Перейти на страницу">
    <button type="button" disabled={current===0} onClick={()=>go(0)} title="Первая страница">«</button>
    <button type="button" disabled={current===0} onClick={()=>go(current-1)} title="Предыдущая страница">‹</button>
    {from>0&&<span className="page-ellipsis">…</span>}
    {pages.map(i=><button key={i} type="button" onClick={()=>go(i)} className={i===current?'is-active':''} aria-current={i===current?'page':undefined}>{i+1}</button>)}
    {from+pages.length<max&&<span className="page-ellipsis">…</span>}
    <button type="button" disabled={current===max-1} onClick={()=>go(current+1)} title="Следующая страница">›</button>
    <button type="button" disabled={current===max-1} onClick={()=>go(max-1)} title="Последняя страница">»</button>
    <form onSubmit={submit} className="page-jump"><label>из {max} <input aria-label="Номер страницы" type="number" inputMode="numeric" min="1" max={max} value={jump} onChange={e=>setJump(e.target.value)}/></label><button type="submit" aria-label="Перейти на страницу">Перейти</button></form>
  </nav>
}
export function PageTitle({eyebrow,title,subtitle,action}) {return <div className="page-heading"><div>{eyebrow&&<span className="eyebrow">{eyebrow}</span>}<h1>{title}</h1>{subtitle&&<p>{subtitle}</p>}</div>{action}</div>}
export function Price({value,suffix='за ночь'}) {return <div className="price"><strong>{formatMoney(value)}</strong>{suffix&&<span>{suffix}</span>}</div>}
export function Layout() {
  const {user,isAdmin,logout,passwordChangeRequired}=useAuth();const [menu,setMenu]=useState(false);const loc=useLocation()
  useEffect(()=>setMenu(false),[loc.pathname])
  return <div className="app-shell"><header className="site-header"><div className="container header-inner"><Link to="/" className="brand"><span className="brand-mark"><BedDouble size={22}/></span>Hotel<span>Booking</span></Link><nav className={`primary-nav ${menu?'nav-open':''}`}><NavLink to="/hotels">Отели</NavLink>{user&&<NavLink to="/account/bookings">Мои поездки</NavLink>}{isAdmin&&<NavLink to="/admin">Админ-панель</NavLink>}</nav><div className="header-actions">{user?<><Link className="profile-link" to="/account"><CircleUserRound size={22}/><span>{passwordChangeRequired ? (user.email || `Учётная запись #${user.id}`) : fullName(user)}</span></Link><button className="icon-btn logout-btn" aria-label="Выйти" title="Выйти" onClick={logout}><LogOut size={18}/></button></>:<><Link to="/login" className="nav-login">Войти</Link><Link to="/register" className="btn btn-primary btn-small">Регистрация</Link></>}<button className="icon-btn mobile-menu" onClick={()=>setMenu(v=>!v)} aria-label="Меню">{menu?<X size={22}/>:<Menu size={22}/>}</button></div></div></header><main><Outlet/></main><footer className="footer"><div className="container footer-inner"><div><Link to="/" className="brand"><span className="brand-mark"><BedDouble size={18}/></span>Hotel<span>Booking</span></Link><p>Ваш комфорт начинается с выбора.</p></div><div><b>Навигация</b><Link to="/hotels">Каталог отелей</Link><Link to="/account/bookings">Мои бронирования</Link></div><div><b>Учебный проект</b><span>Spring Boot · React · API Gateway</span><span>Бронирование без реальной оплаты</span></div></div><div className="container footer-bottom">© {new Date().getFullYear()} Hotel Booking. Демонстрационный сервис.</div></footer></div>
}
export function Protected({admin=false}){const {user,loading,isAdmin,passwordChangeRequired}=useAuth();const loc=useLocation();if(loading) return <Spinner/>;if(!user)return <Navigate to="/login" state={{from:loc}} replace/>;if(passwordChangeRequired)return <Navigate to="/change-password" replace/>;if(admin&&!isAdmin)return <Navigate to="/account" replace/>;return <Outlet/>}
const accountLinks=[['/account',LayoutDashboard,'Обзор'],['/account/bookings',CalendarDays,'Мои бронирования'],['/account/profile',UserRound,'Мой профиль']]
const adminLinks=[['/admin',LayoutDashboard,'Обзор'],['/admin/hotels',Hotel,'Отели'],['/admin/rooms',BedDouble,'Номера'],['/admin/users',UsersRound,'Пользователи'],['/admin/bookings',CalendarDays,'Бронирования'],['/admin/statistics',ChartNoAxesColumn,'Статистика']]
export function DashboardLayout({admin=false}) {const {user,logout}=useAuth();const links=admin?adminLinks:accountLinks;return <div className="container dashboard"><aside className="dashboard-side"><div className="aside-heading"><span className="avatar"><UserRound size={22}/></span><div><strong>{admin?'Администрирование':'Личный кабинет'}</strong><small>{fullName(user)}</small></div></div><nav className="side-links">{links.map(([path,Icon,label])=><NavLink end key={path} to={path}><Icon size={19}/>{label}</NavLink>)}</nav><button className="side-logout" onClick={logout}><LogOut size={18}/>Выйти</button></aside><div className="dashboard-main"><Outlet/></div></div>}
