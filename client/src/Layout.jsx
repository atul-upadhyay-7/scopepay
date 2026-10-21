import React from 'react';
import {NavLink,Outlet,useLocation} from 'react-router-dom';
import {Sidebar,SidebarContent,SidebarFooter,SidebarGroup,SidebarGroupContent,SidebarGroupLabel,SidebarHeader,SidebarMenu,SidebarMenuButton,SidebarMenuItem,SidebarProvider,SidebarTrigger,useSidebar} from '@/components/ui/sidebar';
import {Ambient,Stepper} from './ui.jsx';
import {useScopePay} from './state.jsx';
const LINKS=[['/', 'Home'],['/brief','New brief'],['/review','Review'],['/payment','Payment'],['/receipts','Receipts']];
function Nav(){
 const {pathname}=useLocation();
 const {isMobile,setOpenMobile}=useSidebar();
 return <SidebarMenu>{LINKS.map(([to,label],i)=>{
  const active = to==='/' ? pathname==='/' : pathname.startsWith(to);
  return <SidebarMenuItem key={to}>
   <SidebarMenuButton asChild isActive={active}>
    <NavLink to={to} onClick={()=>isMobile&&setOpenMobile(false)}><span className="idx">0{i+1}</span>{label}</NavLink>
   </SidebarMenuButton>
  </SidebarMenuItem>;})}</SidebarMenu>;
}
function AppSidebar(){
 const {stepDone}=useScopePay();
 return <Sidebar>
  <SidebarHeader>
   <NavLink to="/" className="font-serif text-[27px] font-semibold tracking-tight text-foreground">ScopePay<span className="text-ember">.</span></NavLink>
   <span className="inline-flex w-fit items-center rounded-[3px] border border-ember px-[7px] py-1 font-mono text-[9.5px] font-medium tracking-[1.8px] text-ember">SANDBOX ONLY</span>
  </SidebarHeader>
  <SidebarContent>
   <SidebarGroup><SidebarGroupContent><Nav/></SidebarGroupContent></SidebarGroup>
   <SidebarGroup>
    <SidebarGroupLabel>Progress</SidebarGroupLabel>
    <SidebarGroupContent className="px-2"><Stepper done={stepDone}/></SidebarGroupContent>
   </SidebarGroup>
  </SidebarContent>
  <SidebarFooter>Local demo only. Not escrow, a legal contract or a production payment service. AI never sets prices or authorizes payment.</SidebarFooter>
 </Sidebar>;
}
export default function Layout(){
 const {pathname}=useLocation();
 return <SidebarProvider>
  <Ambient/>
  <AppSidebar/>
  <div className="flex min-w-0 flex-1 flex-col">
   <header className="flex items-center gap-1 border-b bg-sidebar-background px-3 py-2 md:hidden">
    <SidebarTrigger/>
    <span className="ml-1 font-serif text-[19px] font-semibold tracking-tight">ScopePay<span className="text-ember">.</span></span>
   </header>
   <main className="w-full max-w-[940px] flex-1 px-5 py-8 sm:px-10 sm:py-11"><div key={pathname} className="page-enter"><Outlet/></div></main>
   <footer className="border-t px-5 py-5 text-[11.5px] text-muted-foreground sm:px-10 md:hidden">Local demo only. Not escrow, a legal contract or a production payment service. AI never sets prices or authorizes payment.</footer>
  </div>
 </SidebarProvider>;
}
