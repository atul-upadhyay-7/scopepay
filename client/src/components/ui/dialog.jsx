import * as React from 'react';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import {X} from 'lucide-react';
import {cn} from '@/lib/utils';
const Dialog = DialogPrimitive.Root;
const DialogTrigger = DialogPrimitive.Trigger;
const DialogPortal = DialogPrimitive.Portal;
const DialogClose = DialogPrimitive.Close;
const DialogOverlay = React.forwardRef(({className,...props},ref)=>(
 <DialogPrimitive.Overlay ref={ref} className={cn('fixed inset-0 z-50 bg-foreground/40 data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=closed]:animate-out data-[state=closed]:fade-out-0',className)} {...props}/>));
DialogOverlay.displayName = DialogPrimitive.Overlay.displayName;
const DialogContent = React.forwardRef(({className,children,...props},ref)=>(
 <DialogPortal>
  <DialogOverlay/>
  <DialogPrimitive.Content ref={ref} className={cn('fixed left-1/2 top-1/2 z-50 grid w-[calc(100vw-32px)] max-w-lg -translate-x-1/2 -translate-y-1/2 gap-4 rounded-lg border bg-card p-6 sm:p-8 shadow-none focus:outline-none data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95',className)} {...props}>
   {children}
   <DialogPrimitive.Close className="absolute right-5 top-5 rounded-sm text-muted-foreground opacity-80 transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring">
    <X className="h-4 w-4"/><span className="sr-only">Close</span>
   </DialogPrimitive.Close>
  </DialogPrimitive.Content>
 </DialogPortal>));
DialogContent.displayName = DialogPrimitive.Content.displayName;
const DialogHeader = ({className,...props})=>(<div className={cn('flex flex-col space-y-2 text-left',className)} {...props}/>);
const DialogTitle = React.forwardRef(({className,...props},ref)=>(
 <DialogPrimitive.Title ref={ref} className={cn('font-serif text-xl font-semibold tracking-tight',className)} {...props}/>));
DialogTitle.displayName = DialogPrimitive.Title.displayName;
const DialogDescription = React.forwardRef(({className,...props},ref)=>(
 <DialogPrimitive.Description ref={ref} className={cn('text-sm leading-relaxed text-muted-foreground',className)} {...props}/>));
DialogDescription.displayName = DialogPrimitive.Description.displayName;
export {Dialog, DialogPortal, DialogOverlay, DialogTrigger, DialogClose, DialogContent, DialogHeader, DialogTitle, DialogDescription};
