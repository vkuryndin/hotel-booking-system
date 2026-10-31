import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, ArrowRight, BedDouble, Check, CheckCircle2, ImagePlus, Info, Plus, X } from 'lucide-react'
import { api } from '../api/client.js'
import { Alert, Button } from './Shared.jsx'
import { RoomPhotoEditor, RoomNoPhotos, RoomPreviewSlider } from './RoomPhotos.jsx'
import { ROOM_AMENITIES, AMENITY_LABEL } from '../constants/amenities.js'
import { arrayOf, formatMoney } from '../utils/format.js'

const STEPS = ['Данные номера', 'Фотографии', 'Проверка']
const initialRoom = () => ({ number: '', category: 'STANDARD', capacity: '2', pricePerNight: '100', amenities: [] })

function RoomFields({ value, change, firstRef }) {
  return <div className="room-create-fields admin-form">
    <div className="form-row room-create-form-grid">
      <label className="field">Номер *<input ref={firstRef} maxLength={20} required placeholder="Например, 201" value={value.number} onChange={e=>change('number',e.target.value)}/></label>
      <label className="field">Категория<select value={value.category} onChange={e=>change('category',e.target.value)}><option value="STANDARD">STANDARD</option><option value="DELUXE">DELUXE</option><option value="SUITE">SUITE</option></select></label>
      <label className="field">Количество гостей *<input type="number" min="1" max="100" required value={value.capacity} onChange={e=>change('capacity',e.target.value)}/></label>
      <label className="field">Цена за ночь *<input type="number" min="0" step="0.01" required value={value.pricePerNight} onChange={e=>change('pricePerNight',e.target.value)}/></label>
    </div>
    <h4>Удобства номера</h4>
    <div className="amenity-editor" aria-label="Удобства номера">
      {ROOM_AMENITIES.map(item=><label key={item} className={`amenity-choice ${value.amenities.includes(item)?'is-selected':''}`}>
        <input type="checkbox" checked={value.amenities.includes(item)} onChange={e=>change('amenities',e.target.checked ? [...value.amenities,item] : value.amenities.filter(a=>a!==item))}/>
        <span>{AMENITY_LABEL[item]}</span>
      </label>)}
    </div>
  </div>
}

export function RoomCreationWizard({ hotelId, hotelName, onClose, onSaved, roomToEdit = null, initialStep = 0, nested = false }) {
  const editingMode = Boolean(roomToEdit?.id)
  const [step,setStep]=useState(editingMode ? initialStep : 0)
  const [draft,setDraft]=useState(() => roomToEdit ? {
    number:String(roomToEdit.number), category:roomToEdit.category,
    capacity:String(roomToEdit.capacity), pricePerNight:String(roomToEdit.pricePerNight),
    amenities:Array.isArray(roomToEdit.amenities) ? [...roomToEdit.amenities] : []
  } : initialRoom())
  const [savedRoom,setSavedRoom]=useState(roomToEdit)
  const [photos,setPhotos]=useState([])
  const [busy,setBusy]=useState(false)
  const [photoBusy,setPhotoBusy]=useState(false)
  const [error,setError]=useState('')
  const [notice,setNotice]=useState('')
  const modalRef=useRef(null)
  const firstRef=useRef(null)

  useEffect(()=>{
    const old=document.body.style.overflow
    document.body.style.overflow='hidden'
    firstRef.current?.focus()
    return ()=>{document.body.style.overflow=old}
  },[])

  function close() {
    if (busy||photoBusy) return
    if (savedRoom && !editingMode && !window.confirm('Номер уже создан в backend и останется в отеле. Закрыть мастер?')) return
    if (editingMode && step === 0 && JSON.stringify(draft) !== JSON.stringify({number:String(roomToEdit.number), category:roomToEdit.category, capacity:String(roomToEdit.capacity),pricePerNight:String(roomToEdit.pricePerNight),amenities:Array.isArray(roomToEdit.amenities) ? roomToEdit.amenities : []}) && !window.confirm('Закрыть редактирование без сохранения изменений текущего шага?')) return
    if (!savedRoom && (draft.number.trim() || draft.amenities.length) && !window.confirm('Отменить создание номера? Несохранённые данные будут потеряны.')) return
    onClose()
  }

  useEffect(()=>{
    function key(e) {
      if(e.key==='Escape'){e.preventDefault();close()}
      if(e.key==='Tab'&&modalRef.current){
        const items=[...modalRef.current.querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href]')].filter(el=>el.getClientRects().length)
        if (!items.length) return
        if (e.shiftKey && document.activeElement===items[0]){items.at(-1).focus();e.preventDefault()}
        if (!e.shiftKey && document.activeElement===items.at(-1)){items[0].focus();e.preventDefault()}
      }
    }
    document.addEventListener('keydown',key)
    return ()=>document.removeEventListener('keydown',key)
  })

  function change(field,value){setDraft(old=>({...old,[field]:value}));setError('')}
  function validate(){
    const count=Number(draft.capacity), price=Number(draft.pricePerNight)
    if(!draft.number.trim() || draft.number.trim().length>20) return 'Укажите номер (до 20 символов).'
    if(!Number.isInteger(count) || count<1) return 'Вместимость должна быть положительным целым числом.'
    if(!Number.isFinite(price) || price<0 || !/^\d+(?:\.\d{1,2})?$/.test(String(draft.pricePerNight))) return 'Укажите цену не меньше 0 с точностью до копеек.'
    return ''
  }
  async function saveDetails(e) {
    e?.preventDefault()
    if(busy||photoBusy)return
    const problem=validate();if(problem){setError(problem);return}
    setBusy(true);setError('');setNotice('')
    try{
      const details={category:draft.category,capacity:Number(draft.capacity),pricePerNight:Number(draft.pricePerNight),amenities:draft.amenities}
      if(savedRoom){
        if (savedRoom.number!==draft.number.trim()) await api.renameRoom(savedRoom.id,draft.number.trim())
        await api.editRoom(savedRoom.id,details)
        const refreshed=await api.room(savedRoom.id)
        setSavedRoom(refreshed)
        onSaved?.(refreshed)
      } else {
        const room=await api.createRoom({hotelId:Number(hotelId),number:draft.number.trim(),...details})
        if(!room?.id)throw new Error('Сервер не вернул ID номера. Проверьте каталог перед повторным созданием.')
        setSavedRoom(room)
        onSaved?.(room)
      }
      setStep(1)
      setNotice('Номер сохранён. Теперь можно добавить его фотографии.')
    }catch(e){setError(e.message || 'Не удалось сохранить номер.')}finally{setBusy(false)}
  }
  async function showReview(){
    if(busy||photoBusy)return
    setBusy(true);setError('');setNotice('')
    try {setPhotos(arrayOf(await api.roomPhotos(savedRoom.id)));setSavedRoom(await api.room(savedRoom.id));setStep(2)}
    catch(e){setError(e.message)}finally{setBusy(false)}
  }
  function reset(){setDraft(initialRoom());setSavedRoom(null);setPhotos([]);setStep(0);setError('');setNotice('');requestAnimationFrame(()=>firstRef.current?.focus())}
  return <div className={`hotel-wizard-overlay ${nested ? 'nested-room-wizard' : ''}`} role="presentation">
    <section ref={modalRef} className="hotel-wizard room-create-wizard" role="dialog" aria-modal="true" aria-labelledby="room-create-title">
      <header className="hotel-wizard-header">
        <div><h2 id="room-create-title">{editingMode ? `Редактирование номера ${savedRoom?.number || draft.number}` : 'Мастер создания номера'}</h2><p>{hotelName || `Отель #${hotelId}`} · Данные номера, фотографии и проверка</p></div>
        <button type="button" className="wizard-close" disabled={busy||photoBusy} onClick={close}><span>Закрыть</span><X size={20}/></button>
      </header>
      <nav className="wizard-steps" aria-label="Шаги создания номера">{STEPS.map((name,index)=><div key={name} className={`wizard-step ${step===index?'is-active':''} ${step>index?'is-done':''}`} aria-current={step===index?'step':undefined}>
        <span className="wizard-step-number">{index<step?<Check size={18}/>:index+1}</span><span className="wizard-step-label">{name}</span>
      </div>)}</nav>
      <div className="hotel-wizard-body room-create-body">
        {step===0&&<>
          <div className="wizard-section-head"><h3>Шаг 1. Основные данные номера</h3><p>{editingMode ? 'Измените данные номера, затем проверьте фотографии.' : 'Укажите данные номера и сохраните его в отеле.'}</p></div>
          <div className="room-create-columns">
            <form id="room-creation-details" onSubmit={saveDetails}><RoomFields value={draft} change={change} firstRef={firstRef}/></form>
            <aside className="wizard-preview room-create-preview">{savedRoom?.id ? <RoomPreviewSlider roomId={savedRoom.id} number={draft.number}/> : <div className="room-create-empty-preview"><RoomNoPhotos/></div>}<b>Номер {draft.number || '—'}</b><p>{draft.category} · {draft.capacity || '—'} гостей</p><strong>{formatMoney(draft.pricePerNight)} / ночь</strong><span>{savedRoom?.id ? 'Фотографии этого номера' : 'Предпросмотр номера'}</span></aside>
          </div>
          {savedRoom&&<div className="wizard-tip"><Info size={19}/><div><b>Номер уже создан</b><p>Изменения на этом шаге обновят существующий номер #{savedRoom.id}, а не создадут дубликат.</p></div></div>}
        </>}
        {step===1&&<>
          <div className="wizard-section-head"><h3>Шаг 2. Фотографии номера {savedRoom.number}</h3><p>Управляйте фотографиями номера (JPEG, PNG, WebP до 5 МБ). Шаг можно пропустить.</p></div>
          <div className="room-create-photobox"><RoomPhotoEditor key={savedRoom.id} roomId={savedRoom.id} hotelId={hotelId} roomNumber={savedRoom.number} onBusyChange={setPhotoBusy}/></div>
          <div className="wizard-tip"><Info size={19}/><div><b>Сохранение</b><p>Фото сохраняются сразу в Hotel Service. При закрытии мастера номер и загруженные фото останутся в каталоге.</p></div></div>
        </>}
        {step===2&&<>
          <div className="wizard-section-head"><h3>Шаг 3. Проверка</h3><p>Данные номера сохранены в Hotel Service.</p></div>
          <div className="room-create-review">
            <div className="room-create-review-photo"><RoomPreviewSlider roomId={savedRoom.id} number={savedRoom.number} initialPhotos={photos} showAllThumbnails/></div>
            <div className="room-create-review-info"><h3>Номер {savedRoom.number}</h3><p>{hotelName}</p>
              <dl><div><dt>Категория</dt><dd>{savedRoom.category}</dd></div><div><dt>Вместимость</dt><dd>{savedRoom.capacity} гостей</dd></div><div><dt>Цена за ночь</dt><dd>{formatMoney(savedRoom.pricePerNight)}</dd></div><div><dt>Фотографии</dt><dd>{photos.length} / 10</dd></div></dl>
              <div className="room-create-tags">{(savedRoom.amenities||[]).map(item=><span key={item}><CheckCircle2 size={14}/>{AMENITY_LABEL[item]||item}</span>)}</div>
            </div>
          </div>
          <div className="wizard-done-info"><CheckCircle2 size={19}/><span>Номер #{savedRoom.id} сохранён. {editingMode ? 'Вернитесь к отелю.' : 'Можно создать следующий номер без закрытия мастера.'}</span></div>
        </>}
        {(error||notice)&&<div className="wizard-status"><Alert error={error} success={!error?notice:undefined}/></div>}
      </div>
      <footer className="hotel-wizard-footer"><span>Шаг {step+1} из {STEPS.length}{savedRoom&&<small> · Номер #{savedRoom.id} создан</small>}</span>
        <div className="wizard-footer-actions">
          {step===0?<Button variant="outline" type="button" disabled={busy||photoBusy} onClick={close}>Отмена</Button>:<Button variant="outline" type="button" disabled={busy||photoBusy} onClick={()=>{setStep(step-1);setError('');setNotice('')}}><ArrowLeft size={16}/> Назад</Button>}
          {step===0?<Button form="room-creation-details" type="submit" disabled={busy||photoBusy}>{busy?'Сохраняем...':editingMode ? 'Сохранить и далее' : 'Создать номер и далее'} <ArrowRight size={16}/></Button>
            :step===1?<Button type="button" disabled={busy||photoBusy} onClick={showReview}>{busy?'Загружаем...':'Далее'} <ArrowRight size={16}/></Button>
            :<>{!editingMode && <Button type="button" variant="outline" disabled={busy} onClick={reset}><Plus size={16}/> Создать ещё номер</Button>}<Button type="button" disabled={busy} onClick={onClose}>Готово <Check size={17}/></Button></>}
        </div>
      </footer>
    </section>
  </div>
}
