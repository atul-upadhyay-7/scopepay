import React from 'react';
import {AgGridReact} from 'ag-grid-react';
import {AllCommunityModule, ModuleRegistry, themeQuartz} from 'ag-grid-community';

ModuleRegistry.registerModules([AllCommunityModule]);

// Scoped to the ScopePay ember palette so the grid reads as part of the product,
// not a dropped-in widget.
const scopeTheme = themeQuartz.withParams({
  backgroundColor: '#1f1813',
  foregroundColor: '#f4ede4',
  headerBackgroundColor: '#191310',
  headerTextColor: '#d9cfc2',
  borderColor: '#332920',
  rowHoverColor: '#241c16',
  selectedRowBackgroundColor: '#33241a',
  accentColor: '#ff6a2f',
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
