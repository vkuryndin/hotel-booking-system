import { createContext, useContext, useEffect, useState, useCallback } from 'react'
import { api, tokenStorage } from '../api/client.js'

const AuthContext = createContext(null)
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
  const [user,setUser]=useState(() => tokenStorage.get() ? fromToken(tokenStorage.get()) : null)
  const [loading,setLoading]=useState(Boolean(tokenStorage.get()))
  const logout=useCallback(() => { tokenStorage.clear(); setToken(null);setUser(null);setLoading(false) },[])
  useEffect(()=>{
    const onExpire=()=>logout()
    window.addEventListener('hotel:auth-expired',onExpire)
    return ()=>window.removeEventListener('hotel:auth-expired',onExpire)
  },[logout])
  useEffect(()=>{
    if (!token) return
    let active=true
    api.me().then(data=>{if(active) setUser(old=>({...old,...data,role:roleFrom(data,parseJwt(token))}))}).catch(e=>{if(active && e.status===401) logout()}).finally(()=>{if(active) setLoading(false)})
    return ()=>{active=false}
  },[token,logout])
  async function login(email,password){
    const response=await api.login({email,password})
    const raw=response?.accessToken || response?.token
    if(!raw) throw Error('Сервер не вернул accessToken')
    tokenStorage.set(raw)
    setUser(fromToken(raw,response))
    setToken(raw)
    return fromToken(raw,response)
  }
  async function register(form){return api.register(form)}
  async function refreshUser(){const data=await api.me();setUser(old=>({...old,...data}));return data}
  return <AuthContext.Provider value={{token,user,loading,login,logout,register,refreshUser,isAdmin:user?.role==='ADMIN'}}>{children}</AuthContext.Provider>
}
export const useAuth=()=>useContext(AuthContext)
