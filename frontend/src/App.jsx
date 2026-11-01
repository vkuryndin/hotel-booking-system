import { Navigate, Route, Routes, Link } from 'react-router-dom'
import { DashboardLayout, Layout, Protected } from './components/Shared.jsx'
import { CatalogPage, HomePage, HotelPage } from './pages/Public.jsx'
import { AccountBookings, AccountOverview, AccountProfile, AuthPage, BookingPage } from './pages/Account.jsx'
import { AdminBookings, AdminOverview, AdminRooms, AdminStatistics } from './pages/Admin.jsx'
import { AdminHotels } from './pages/AdminHotels.jsx'
import { AdminUsers } from './pages/AdminUsers.jsx'

function NotFound(){return <div className="container section-space not-found"><span>404</span><h1>Страница не найдена</h1><p>Возможно, адрес изменился.</p><Link className="btn btn-primary" to="/">На главную</Link></div>}
export default function App(){return <Routes><Route element={<Layout/>}>
  <Route index element={<HomePage/>}/>
  <Route path="hotels" element={<CatalogPage/>}/>
  <Route path="hotels/:id" element={<HotelPage/>}/>
  <Route path="login" element={<AuthPage mode="login"/>}/>
  <Route path="register" element={<AuthPage mode="register"/>}/>
  <Route element={<Protected/>}>
    <Route path="book/:hotelId" element={<BookingPage/>}/>
    <Route path="account" element={<DashboardLayout/>}>
      <Route index element={<AccountOverview/>}/>
      <Route path="bookings" element={<AccountBookings/>}/>
      <Route path="profile" element={<AccountProfile/>}/>
    </Route>
  </Route>
  <Route element={<Protected admin/>}>
    <Route path="admin" element={<DashboardLayout admin/>}>
      <Route index element={<AdminOverview/>}/>
      <Route path="hotels" element={<AdminHotels/>}/>
      <Route path="rooms" element={<AdminRooms/>}/>
      <Route path="users" element={<AdminUsers/>}/>
      <Route path="photos" element={<Navigate to="/admin/hotels" replace/>}/>
      <Route path="bookings" element={<AdminBookings/>}/>
      <Route path="statistics" element={<AdminStatistics/>}/>
    </Route>
  </Route>
  <Route path="bookings" element={<Navigate to="/account/bookings" replace/>}/>
  <Route path="*" element={<NotFound/>}/>
</Route></Routes>}
