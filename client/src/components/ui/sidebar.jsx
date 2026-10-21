import * as React from 'react';
import {Slot} from '@radix-ui/react-slot';
import {cva} from 'class-variance-authority';
import {PanelLeft} from 'lucide-react';
import {useIsMobile} from '@/hooks/use-mobile';
import {cn} from '@/lib/utils';
import {Button} from '@/components/ui/button';
import {Dialog, DialogContent, DialogTitle} from '@/components/ui/dialog';
import {Separator} from '@/components/ui/separator';
import {Tooltip, TooltipContent, TooltipProvider, TooltipTrigger} from '@/components/ui/tooltip';

const SIDEBAR_WIDTH = '250px';
const SidebarContext = React.createContext(null);
function useSidebar(){
 const context = React.useContext(SidebarContext);
 if(!context) throw new Error('useSidebar must be used within a SidebarProvider.');
 return context;
}

const SidebarProvider = React.forwardRef(({className,style,children,...props},ref)=>{
 const isMobile = useIsMobile();
 const [openMobile,setOpenMobile] = React.useState(false);
 const [open,setOpen] = React.useState(true);
 const toggleSidebar = React.useCallback(()=>isMobile?setOpenMobile(o=>!o):setOpen(o=>!o),[isMobile]);
 const value = React.useMemo(()=>({state:open?'expanded':'collapsed',open,setOpen,openMobile,setOpenMobile,isMobile,toggleSidebar}),[open,isMobile,openMobile,toggleSidebar]);
 return (
  <SidebarContext.Provider value={value}>
   <TooltipProvider delayDuration={0}>
    <div ref={ref} style={{'--sidebar-width':SIDEBAR_WIDTH,...style}} className={cn('group/sidebar-wrapper flex min-h-screen w-full',className)} {...props}>
     {children}
    </div>
   </TooltipProvider>
  </SidebarContext.Provider>);
});
SidebarProvider.displayName = 'SidebarProvider';

const Sidebar = React.forwardRef(({className,children,...props},ref)=>{
 const {isMobile,openMobile,setOpenMobile,open} = useSidebar();
 if(isMobile){
  return (
   <Dialog open={openMobile} onOpenChange={setOpenMobile}>
    <DialogContent className="fixed left-0 top-0 h-full w-[280px] max-w-[280px] translate-x-0 translate-y-0 rounded-none border-y-0 border-l-0 p-0 data-[state=open]:slide-in-from-left" aria-describedby={undefined}>
     <DialogTitle className="sr-only">ScopePay navigation</DialogTitle>
     <div className="flex h-full w-full flex-col bg-sidebar-background">{children}</div>
    </DialogContent>
   </Dialog>);
 }
 return (
  <aside ref={ref} className={cn('dot-grain sticky top-0 hidden h-screen w-[var(--sidebar-width)] shrink-0 flex-col border-r bg-sidebar-background text-sidebar-foreground transition-[width] md:flex',!open&&'w-0 border-r-0 overflow-hidden',className)} {...props}>
   {children}
  </aside>);
});
Sidebar.displayName = 'Sidebar';

const SidebarHeader = React.forwardRef(({className,...props},ref)=>(
 <div ref={ref} className={cn('flex flex-col gap-3 p-5 pt-6',className)} {...props}/>));
SidebarHeader.displayName = 'SidebarHeader';
const SidebarFooter = React.forwardRef(({className,...props},ref)=>(
 <div ref={ref} className={cn('mt-auto flex flex-col gap-2 border-t p-5 text-[11px] leading-relaxed text-muted-foreground',className)} {...props}/>));
SidebarFooter.displayName = 'SidebarFooter';
const SidebarContent = React.forwardRef(({className,...props},ref)=>(
 <div ref={ref} className={cn('flex min-h-0 flex-1 flex-col gap-2 overflow-auto px-3',className)} {...props}/>));
SidebarContent.displayName = 'SidebarContent';
const SidebarGroup = React.forwardRef(({className,...props},ref)=>(
 <div ref={ref} className={cn('relative flex w-full min-w-0 flex-col py-2',className)} {...props}/>));
SidebarGroup.displayName = 'SidebarGroup';
const SidebarGroupLabel = React.forwardRef(({className,...props},ref)=>(
 <div ref={ref} className={cn('px-2 pb-2 font-mono text-[10px] font-medium uppercase tracking-[1.6px] text-muted-foreground',className)} {...props}/>));
SidebarGroupLabel.displayName = 'SidebarGroupLabel';
const SidebarGroupContent = React.forwardRef(({className,...props},ref)=>(
 <div ref={ref} className={cn('w-full',className)} {...props}/>));
SidebarGroupContent.displayName = 'SidebarGroupContent';
const SidebarMenu = React.forwardRef(({className,...props},ref)=>(
 <ul ref={ref} className={cn('flex w-full min-w-0 flex-col gap-0.5',className)} {...props}/>));
SidebarMenu.displayName = 'SidebarMenu';
const SidebarMenuItem = React.forwardRef(({className,...props},ref)=>(
 <li ref={ref} className={cn('group/menu-item relative',className)} {...props}/>));
SidebarMenuItem.displayName = 'SidebarMenuItem';
const sidebarMenuButtonVariants = cva(
 'peer/menu-button relative flex w-full items-baseline gap-2.5 rounded-[5px] px-2 py-2 text-left text-[13.5px] font-medium text-sidebar-foreground transition-all duration-200 hover:bg-secondary hover:pl-3 focus-visible:ring-2 focus-visible:ring-sidebar-ring data-[active=true]:bg-sidebar-accent data-[active=true]:text-sidebar-accent-foreground before:absolute before:left-0 before:top-1/2 before:h-0 before:w-[2.5px] before:-translate-y-1/2 before:rounded-full before:bg-ember before:opacity-0 before:transition-all before:duration-200 hover:before:h-3 hover:before:opacity-60 data-[active=true]:before:h-4 data-[active=true]:before:opacity-100 [&>span.idx]:font-mono [&>span.idx]:text-[10px] [&>span.idx]:text-muted-foreground data-[active=true]:[&>span.idx]:text-ember',
 {variants:{size:{default:'',sm:'text-xs'}}});
const SidebarMenuButton = React.forwardRef(({asChild=false,isActive,className,...props},ref)=>{
 const Comp = asChild ? Slot : 'button';
 return <Comp ref={ref} data-active={isActive} className={cn(sidebarMenuButtonVariants({className}))} {...props}/>;
});
SidebarMenuButton.displayName = 'SidebarMenuButton';
const SidebarSeparator = React.forwardRef(({className,...props},ref)=>(
 <Separator ref={ref} className={cn('mx-2 w-auto bg-sidebar-border',className)} {...props}/>));
SidebarSeparator.displayName = 'SidebarSeparator';
const SidebarTrigger = React.forwardRef(({className,onClick,...props},ref)=>{
 const {toggleSidebar} = useSidebar();
 return (
  <Button ref={ref} variant="ghost" size="icon" className={cn('h-8 w-8',className)}
   onClick={e=>{onClick?.(e);toggleSidebar();}} {...props}>
   <PanelLeft/><span className="sr-only">Toggle sidebar</span>
  </Button>);
});
SidebarTrigger.displayName = 'SidebarTrigger';
export {Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupContent, SidebarGroupLabel, SidebarHeader, SidebarMenu, SidebarMenuButton, SidebarMenuItem, SidebarProvider, SidebarSeparator, SidebarTrigger, useSidebar};
