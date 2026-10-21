import React from 'react';
import {AgGridReact} from 'ag-grid-react';
import {AllCommunityModule, ModuleRegistry, themeQuartz} from 'ag-grid-community';

ModuleRegistry.registerModules([AllCommunityModule]);

// Scoped to the ScopePay paper/ink palette so the grid reads as part of the product,
// not a dropped-in widget.
const scopeTheme = themeQuartz.withParams({
  backgroundColor: '#fbf9f0',
  foregroundColor: '#191c16',
  headerBackgroundColor: '#ebe7d7',
  headerTextColor: '#3d443a',
  borderColor: '#ddd7c3',
  rowHoverColor: '#ece8d8',
  selectedRowBackgroundColor: '#dde6d2',
  accentColor: '#1c5c3d',
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
