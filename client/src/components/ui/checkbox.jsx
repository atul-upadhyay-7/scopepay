import * as React from 'react';
import * as CheckboxPrimitive from '@radix-ui/react-checkbox';
import {Check} from 'lucide-react';
import {cn} from '@/lib/utils';
const Checkbox = React.forwardRef(({className,...props},ref)=>(
 <CheckboxPrimitive.Root ref={ref} className={cn('peer mt-0.5 h-[18px] w-[18px] shrink-0 rounded-[4px] border border-input focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:border-green data-[state=checked]:bg-green data-[state=checked]:text-primary-foreground',className)} {...props}>
  <CheckboxPrimitive.Indicator className="flex items-center justify-center"><Check className="h-3.5 w-3.5" strokeWidth={3}/></CheckboxPrimitive.Indicator>
 </CheckboxPrimitive.Root>));
Checkbox.displayName = CheckboxPrimitive.Root.displayName;
export {Checkbox};
