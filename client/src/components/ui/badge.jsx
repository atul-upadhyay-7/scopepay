import * as React from 'react';
import {cva} from 'class-variance-authority';
import {cn} from '@/lib/utils';
const badgeVariants = cva(
 'inline-flex items-center rounded-sm border px-2 py-0.5 font-mono text-[10px] font-medium uppercase tracking-[1.2px] transition-colors',
 {variants:{variant:{
  default:'border-border text-secondary-foreground',
  ok:'border-ember bg-accent text-accent-foreground',
  warn:'border-warn bg-warn-soft text-warn',
  outline:'border-input text-muted-foreground',
 }},defaultVariants:{variant:'default'}});
function Badge({className,variant,...props}){return <span className={cn(badgeVariants({variant}),className)} {...props}/>;}
export {Badge, badgeVariants};
