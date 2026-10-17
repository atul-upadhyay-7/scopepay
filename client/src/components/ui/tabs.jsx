import * as React from 'react';
import * as TabsPrimitive from '@radix-ui/react-tabs';
import {cn} from '@/lib/utils';
const Tabs = TabsPrimitive.Root;
const TabsList = React.forwardRef(({className,...props},ref)=>(
 <TabsPrimitive.List ref={ref} className={cn('inline-flex h-10 items-center gap-1 rounded-md border bg-card p-1',className)} {...props}/>));
TabsList.displayName = TabsPrimitive.List.displayName;
const TabsTrigger = React.forwardRef(({className,...props},ref)=>(
 <TabsPrimitive.Trigger ref={ref} className={cn('inline-flex items-center justify-center whitespace-nowrap rounded-[4px] px-3.5 py-1.5 text-[13px] font-medium text-muted-foreground transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50 data-[state=active]:bg-accent data-[state=active]:text-accent-foreground',className)} {...props}/>));
TabsTrigger.displayName = TabsPrimitive.Trigger.displayName;
const TabsContent = React.forwardRef(({className,...props},ref)=>(
 <TabsPrimitive.Content ref={ref} className={cn('mt-5 focus-visible:outline-none',className)} {...props}/>));
TabsContent.displayName = TabsPrimitive.Content.displayName;
export {Tabs, TabsList, TabsTrigger, TabsContent};
