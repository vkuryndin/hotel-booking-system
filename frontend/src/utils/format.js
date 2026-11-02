import { AMENITY_LABEL } from '../constants/amenities.js'
export const arrayOf = value => Array.isArray(value) ? value : Array.isArray(value?.content) ? value.content : Array.isArray(value?.items) ? value.items : []
export const formatMoney = value => {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  return Number.isFinite(n) ? `${new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(n)} у.е.` : String(value)
}
export const formatDate = date => {
  if (!date) return '—'
  const [year, month, day] = String(date).slice(0,10).split('-').map(Number)
  if (!year || !month || !day) return String(date)
  return new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(year,month-1,day))
}
export const dateToISO = d => [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-')
export const today = () => dateToISO(new Date())
export const plusDays = (days, from = new Date()) => { const d = new Date(from); d.setDate(d.getDate()+days); return dateToISO(d) }
export const nights = (checkIn, checkOut) => {
  if (!checkIn || !checkOut) return 0
  return Math.round((new Date(`${checkOut}T12:00:00Z`) - new Date(`${checkIn}T12:00:00Z`)) / 86400000)
}
export const validDates = (a,b) => a && b && a >= today() && nights(a,b) > 0 && nights(a,b) <= 365
export const toQuery = obj => new URLSearchParams(Object.entries(obj).filter(([,v]) => v !== '' && v !== undefined && v !== null)).toString()
export const statusName = value => ({ CONFIRMED: 'Подтверждено', CANCELLED: 'Отменено', PENDING: 'В обработке', CANCELLING: 'Отмена выполняется' }[value] || value || 'Неизвестно')
export const statusTone = value => value === 'CONFIRMED' ? 'success' : value === 'CANCELLED' ? 'danger' : 'warning'
export const amenityNames = AMENITY_LABEL
export const amenity = name => amenityNames[name] || String(name).replaceAll('_',' ').toLowerCase()
export const fullName = user => [user?.firstName,user?.lastName].filter(Boolean).join(' ') || user?.username || user?.email?.split('@')[0] || 'Пользователь'
export const normalRoom = room => ({...room, number: room?.number ?? room?.roomNumber ?? '—', category: room?.category ?? 'STANDARD', pricePerNight: room?.pricePerNight ?? 0})
export const getHotelId = room => room?.hotelId ?? room?.hotel?.id
