import { Component } from 'react'
import { CircleAlert, RefreshCcw } from 'lucide-react'

/** A render failure must not leave the user on an empty page. */
export class ErrorBoundary extends Component {
  constructor(props){super(props);this.state={hasError:false}}
  static getDerivedStateFromError(){return {hasError:true}}
  componentDidCatch(error,info){
    if(import.meta.env.DEV) console.error('Ошибка отображения страницы:',error,info)
  }
  retry=()=>this.setState({hasError:false})
  render(){
    if(this.state.hasError)return <div className="container section-space render-error" role="alert">
      <CircleAlert size={36}/><h2>Не удалось отобразить страницу</h2>
      <p>Данные не потеряны. Попробуйте открыть страницу снова или вернуться в каталог.</p>
      <div className="render-error-actions"><button className="btn btn-primary" type="button" onClick={this.retry}><RefreshCcw size={17}/> Повторить</button><a className="btn btn-outline" href="/hotels">К каталогу</a></div>
    </div>
    return this.props.children
  }
}
