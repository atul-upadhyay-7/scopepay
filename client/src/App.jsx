import React from 'react';
import {Routes,Route} from 'react-router-dom';
import Layout from './Layout.jsx';
import Home from './pages/Home.jsx';
import Brief from './pages/Brief.jsx';
import Review from './pages/Review.jsx';
import Payment from './pages/Payment.jsx';
import Receipts from './pages/Receipts.jsx';
import {ScopePayProvider} from './state.jsx';
import {Toaster} from '@/components/ui/sonner';
export default function App(){
 return <ScopePayProvider><Routes><Route element={<Layout/>}>
  <Route path="/" element={<Home/>}/>
  <Route path="/brief" element={<Brief/>}/>
  <Route path="/review" element={<Review/>}/>
  <Route path="/payment" element={<Payment/>}/>
  <Route path="/receipts" element={<Receipts/>}/>
  <Route path="*" element={<Home/>}/>
 </Route></Routes><Toaster position="bottom-right"/></ScopePayProvider>;
}
