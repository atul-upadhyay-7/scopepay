import React from 'react';
import {AgGridReact} from 'ag-grid-react';
import {AllCommunityModule, ModuleRegistry, themeQuartz} from 'ag-grid-community';

ModuleRegistry.registerModules([AllCommunityModule]);

// Scoped to the ScopePay paper/ink palette so the grid reads as part of the product,
// not a dropped-in widget.
const scopeTheme = themeQuartz.withParams({
  backgroundColor: '#fffefa',
  foregroundColor: '#191c16',
  headerBackgroundColor: '#f6f5ef',
  headerTextColor: '#3c443a',
  borderColor: '#e3e1d4',
  rowHoverColor: '#efeee6',
  selectedRowBackgroundColor: '#e9efe4',
  accentColor: '#1d4a38',
  fontFamily: {googleFont: 'Inter Variable'},
  headerFontFamily: {googleFont: 'Inter Variable'},
  borderRadius: 8,
  wrapperBorder: false,
  rowHeight: 44,
  headerHeight: 40,
});

export default function ScopeGrid({rowData, columnDefs, onRowClicked, height = 340}) {
  return (
    <div style={{height, width: '100%'}}>
      <AgGridReact
        theme={scopeTheme}
        rowData={rowData}
        columnDefs={columnDefs}
        onRowClicked={onRowClicked}
        rowSelection="single"
        suppressCellFocus
        animateRows
        defaultColDef={{sortable: true, filter: true, resizable: true, flex: 1, minWidth: 110}}
      />
    </div>
  );
}
