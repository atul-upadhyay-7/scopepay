import {Toaster as Sonner} from 'sonner';
const Toaster = ({...props})=>(
 <Sonner
  toastOptions={{style:{background:'var(--card)',color:'var(--foreground)',border:'1px solid var(--border)',borderRadius:'var(--radius)',fontFamily:'var(--font-sans)',boxShadow:'none'}}}
  {...props}/>);
export {Toaster};
