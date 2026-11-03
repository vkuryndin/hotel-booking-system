import { Suspense, lazy } from 'react'
import { Navigate, Route, Routes, Link } from 'react-router-dom'
import { DashboardLayout, Layout, Protected, Spinner } from './components/Shared.jsx'

// Load page groups only when visited. Auth, API client and shared layout stay loaded
// so JWT checks and user actions are not delayed by administration bundles.
const HomePage = lazy(() => import('./pages/Public.jsx').then(m => ({ default: m.HomePage })))
const CatalogPage = lazy(() => import('./pages/Public.jsx').then(m => ({ default: m.CatalogPage })))
const HotelPage = lazy(() => import('./pages/Public.jsx').then(m => ({ default: m.HotelPage })))
const AuthPage = lazy(() => import('./pages/Account.jsx').then(m => ({ default: m.AuthPage })))
const ForcedPasswordChangePage = lazy(() => import('./pages/Account.jsx').then(m => ({ default: m.ForcedPasswordChangePage })))
const BookingPage = lazy(() => import('./pages/Account.jsx').then(m => ({ default: m.BookingPage })))
const AccountOverview = lazy(() => import('./pages/Account.jsx').then(m => ({ default: m.AccountOverview })))
const AccountBookings = lazy(() => import('./pages/Account.jsx').then(m => ({ default: m.AccountBookings })))
const BookingDetailsPage = lazy(() => import('./pages/Account.jsx').then(m => ({ default: m.BookingDetailsPage })))
const AccountProfile = lazy(() => import('./pages/Account.jsx').then(m => ({ default: m.AccountProfile })))
const AdminOverview = lazy(() => import('./pages/Admin.jsx').then(m => ({ default: m.AdminOverview })))
const AdminBookings = lazy(() => import('./pages/Admin.jsx').then(m => ({ default: m.AdminBookings })))
const AdminRooms = lazy(() => import('./pages/Admin.jsx').then(m => ({ default: m.AdminRooms })))
const AdminStatistics = lazy(() => import('./pages/Admin.jsx').then(m => ({ default: m.AdminStatistics })))
const AdminHotels = lazy(() => import('./pages/AdminHotels.jsx').then(m => ({ default: m.AdminHotels })))
const AdminUsers = lazy(() => import('./pages/AdminUsers.jsx').then(m => ({ default: m.AdminUsers })))

function NotFound(){return <div className="container section-space not-found"><span>404</span><h1>Страница не найдена</h1><p>Возможно, адрес изменился.</p><Link className="btn btn-primary" to="/">На главную</Link></div>}
export default function App(){return <Suspense fallback={<div className="container section-space"><Spinner label="Открываем страницу…"/></div>}><Routes><Route element={<Layout/>}>
  <Route index element={<HomePage/>}/>
  <Route path="hotels" element={<CatalogPage/>}/>
  <Route path="hotels/:id" element={<HotelPage/>}/>
  <Route path="login" element={<AuthPage mode="login"/>}/>
  <Route path="register" element={<AuthPage mode="register"/>}/>
  <Route path="change-password" element={<ForcedPasswordChangePage/>}/>
  <Route element={<Protected/>}>
    <Route path="book/:hotelId" element={<BookingPage/>}/>
    <Route path="account" element={<DashboardLayout/>}>
      <Route index element={<AccountOverview/>}/>
      <Route path="bookings" element={<AccountBookings/>}/>
      <Route path="bookings/:id" element={<BookingDetailsPage/>}/>
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
</Route></Routes></Suspense>}
