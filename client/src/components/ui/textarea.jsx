import * as React from 'react';
import {cn} from '@/lib/utils';
const Textarea = React.forwardRef(({className,...props},ref)=>(
 <textarea ref={ref} className={cn('flex min-h-[190px] w-full rounded-md border border-input bg-card transition-colors duration-200 hover:border-muted-foreground/50 focus-visible:border-green px-4 py-3.5 text-[15px] leading-relaxed placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50',className)} {...props}/>));
Textarea.displayName = 'Textarea';
export {Textarea};
