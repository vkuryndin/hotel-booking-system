import { createContext, useContext, useEffect, useState, useCallback } from 'react'
import { api, tokenStorage } from '../api/client.js'
import { clearLoginCooldown, saveLoginCooldown } from '../utils/loginRateLimit.js'

const AuthContext = createContext(null)
const FORCE_PASSWORD_KEY = 'hotel_booking_password_change_required'
const FORCE_LOGIN_EMAIL_KEY = 'hotel_booking_forced_login_email'
const forcedPasswordStorage = {
  get: () => sessionStorage.getItem(FORCE_PASSWORD_KEY) === 'true',
  set: value => value ? sessionStorage.setItem(FORCE_PASSWORD_KEY, 'true') : sessionStorage.removeItem(FORCE_PASSWORD_KEY),
}

const parseJwt = token => {
  try { const part=token.split('.')[1]; const normalized=part.replace(/-/g,'+').replace(/_/g,'/'); return JSON.parse(atob(normalized)) } catch { return {} }
}
const roleFrom = (data,claims) => {
  const value = data?.role || data?.user?.role || claims?.role || claims?.roles || claims?.authorities || 'USER'
  return Array.isArray(value) ? (value.some(v=>String(v).includes('ADMIN')) ? 'ADMIN':'USER') : String(value).replace(/^ROLE_/,'').toUpperCase()
}
const fromToken = (raw, response={}) => {
  const claims=parseJwt(raw)
  const identity=response?.user || response
  return { id:identity?.id || claims?.userId || claims?.sub, email:identity?.email || claims?.email || '', username:identity?.username || claims?.preferred_username || '', firstName:identity?.firstName || claims?.firstName || '', lastName:identity?.lastName || claims?.lastName || '', role:roleFrom(response,claims) }
}
export function AuthProvider({children}) {
  const [token,setToken]=useState(tokenStorage.get())
  const [user,setUser]=useState(() => {
    if (!tokenStorage.get()) return null
    const saved=fromToken(tokenStorage.get())
    const forcedEmail=forcedPasswordStorage.get() ? sessionStorage.getItem(FORCE_LOGIN_EMAIL_KEY) : null
    return forcedEmail ? {...saved,email:forcedEmail} : saved
  })
  const [loading,setLoading]=useState(Boolean(tokenStorage.get()) && !forcedPasswordStorage.get())
  const [passwordChangeRequired,setPasswordChangeRequired]=useState(() => forcedPasswordStorage.get())
  const logout=useCallback(() => { tokenStorage.clear();forcedPasswordStorage.set(false);sessionStorage.removeItem(FORCE_LOGIN_EMAIL_KEY);setPasswordChangeRequired(false);setToken(null);setUser(null);setLoading(false) },[])
  useEffect(()=>{
    const onExpire=()=>logout()
    const onForce=()=>{forcedPasswordStorage.set(true);setPasswordChangeRequired(true);setLoading(false)}
    window.addEventListener('hotel:auth-expired',onExpire)
    window.addEventListener('hotel:password-change-required',onForce)
    return ()=>{
      window.removeEventListener('hotel:auth-expired',onExpire)
      window.removeEventListener('hotel:password-change-required',onForce)
    }
  },[logout])
  useEffect(()=>{
    if (!token || passwordChangeRequired) {setLoading(false);return}
    let active=true
    api.me().then(data=>{if(active) setUser(old=>({...old,...data,role:roleFrom(data,parseJwt(token))}))}).catch(e=>{if(active && e.status===401) logout()}).finally(()=>{if(active) setLoading(false)})
    return ()=>{active=false}
  },[token,logout,passwordChangeRequired])
  async function login(email,password){
    const response=await api.login({email,password}).catch(err=>{
      if(err.status===429) saveLoginCooldown(err.retryAfterSeconds)
      throw err
    })
    clearLoginCooldown()
    const raw=response?.accessToken || response?.token
    if(!raw) throw Error('Сервер не вернул accessToken')
    tokenStorage.set(raw)
    const mustChange = response.passwordChangeRequired === true
    forcedPasswordStorage.set(mustChange)
    const loginEmail=email.trim().toLowerCase()
    if (mustChange) sessionStorage.setItem(FORCE_LOGIN_EMAIL_KEY, loginEmail)
    else sessionStorage.removeItem(FORCE_LOGIN_EMAIL_KEY)
    setPasswordChangeRequired(mustChange)
    const identity={...fromToken(raw,response),email:response?.email || loginEmail}
    setUser(identity)
    setToken(raw)
    return {...identity, passwordChangeRequired: mustChange}
  }
  async function register(form){return api.register(form)}
  async function refreshUser(){const data=await api.me();setUser(old=>({...old,...data}));return data}
  return <AuthContext.Provider value={{token,user,loading,login,logout,register,refreshUser,passwordChangeRequired,isAdmin:user?.role==='ADMIN'}}>{children}</AuthContext.Provider>
}
export const useAuth=()=>useContext(AuthContext)
