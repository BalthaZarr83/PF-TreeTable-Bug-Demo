package org.primefaces.test;

import org.primefaces.PrimeFaces;
import org.primefaces.component.api.DynamicColumn;
import org.primefaces.component.api.UIColumn;
import org.primefaces.component.celleditor.CellEditor;
import org.primefaces.component.column.Column;
import org.primefaces.component.columngroup.ColumnGroup;
import org.primefaces.component.columns.Columns;
import org.primefaces.component.row.Row;
import org.primefaces.component.treetable.TreeTable;
import org.primefaces.component.treetable.TreeTableState;
import org.primefaces.model.*;
import org.primefaces.model.filter.FilterConstraint;
import org.primefaces.model.filter.FunctionFilterConstraint;
import org.primefaces.renderkit.DataRenderer;
import org.primefaces.renderkit.RendererUtils;
import org.primefaces.util.*;
import org.primefaces.visit.ResetInputVisitCallback;

import javax.el.ELContext;
import javax.faces.FacesException;
import javax.faces.component.EditableValueHolder;
import javax.faces.component.UIComponent;
import javax.faces.component.UINamingContainer;
import javax.faces.component.ValueHolder;
import javax.faces.component.visit.VisitContext;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class is only added to prove that that the bug is independent of the fix for the cloneTreeNode method by melloware; only line 1358 is added
 * Sorry for using the decompiled code:-) to be a bit faster
 */
public class TreeTableRenderer extends DataRenderer {
    private static final Logger LOGGER = Logger.getLogger(TreeTableRenderer.class.getName());
    private static final String SB_DECODE_SELECTION = TreeTableRenderer.class.getName() + "#decodeSelection";

    public TreeTableRenderer() {
    }

    public void decode(FacesContext context, UIComponent component) {
        TreeTable tt = (TreeTable)component;
        if (tt.getSelectionMode() != null) {
            TreeNode root = tt.getValue();
            this.decodeSelection(context, tt, root);
        }

        if (tt.isSortRequest(context)) {
            this.decodeSort(context, tt);
        }

        tt.decodeColumnResizeState(context);
        this.decodeBehaviors(context, component);
    }

    protected void decodeSelection(FacesContext context, TreeTable tt, TreeNode root) {
        Map<String, String> params = context.getExternalContext().getRequestParameterMap();
        String selectionMode = tt.getSelectionMode();
        String clientId = tt.getClientId(context);
        String selectedNodeRowKey;
        String[] selectedRowKeys;
        if (selectionMode != null) {
            selectedNodeRowKey = (String)params.get(tt.getClientId(context) + "_selection");
            boolean isSingle = "single".equalsIgnoreCase(selectionMode);
            if (this.isValueBlank(selectedNodeRowKey)) {
                if (isSingle) {
                    tt.setSelection((Object)null);
                } else {
                    tt.setSelection(new TreeNode[0]);
                }
            } else {
                selectedRowKeys = selectedNodeRowKey.split(",");
                if (isSingle) {
                    tt.setRowKey(root, selectedRowKeys[0]);
                    tt.setSelection(tt.getRowNode());
                } else {
                    List<TreeNode> selectedNodes = new ArrayList();

                    for(int i = 0; i < selectedRowKeys.length; ++i) {
                        tt.setRowKey(root, selectedRowKeys[i]);
                        TreeNode rowNode = tt.getRowNode();
                        if (rowNode != null) {
                            selectedNodes.add(rowNode);
                        }
                    }

                    tt.setSelection(selectedNodes.toArray(new TreeNode[selectedNodes.size()]));
                }

                tt.setRowKey(root, (String)null);
            }
        }

        if (tt.isCheckboxSelection() && tt.isSelectionRequest(context)) {
            selectedNodeRowKey = (String)params.get(clientId + "_instantSelection");
            tt.setRowKey(root, selectedNodeRowKey);
            TreeNode selectedNode = tt.getRowNode();
            List<String> descendantRowKeys = new ArrayList();
            tt.populateRowKeys(selectedNode, descendantRowKeys);
            int size = descendantRowKeys.size();
            StringBuilder sb = SharedStringBuilder.get(context, SB_DECODE_SELECTION);

            for(int i = 0; i < size; ++i) {
                sb.append((String)descendantRowKeys.get(i));
                if (i != size - 1) {
                    sb.append(",");
                }
            }

            PrimeFaces.current().ajax().addCallbackParam("descendantRowKeys", sb.toString());
            sb.setLength(0);
            selectedRowKeys = null;
        }

    }

    public void encodeEnd(FacesContext context, UIComponent component) throws IOException {
        TreeTable tt = (TreeTable)component;
        TreeNode root = tt.getValue();
        String clientId = tt.getClientId(context);
        Map<String, String> params = context.getExternalContext().getRequestParameterMap();
        this.preRender(context, tt);
        String rppValue;
        TreeNode node;
        if (tt.isExpandRequest(context)) {
            rppValue = (String)params.get(clientId + "_expand");
            tt.setRowKey(root, rppValue);
            node = tt.getRowNode();
            node.setExpanded(true);
            if (tt.getExpandMode().equals("self")) {
                this.encodeNode(context, tt, root, node);
            } else {
                this.encodeNodeChildren(context, tt, root, node);
            }
        } else if (tt.isCollapseRequest(context)) {
            rppValue = (String)params.get(clientId + "_collapse");
            tt.setRowKey(root, rppValue);
            node = tt.getRowNode();
            node.setExpanded(false);
        } else if (tt.isFilterRequest(context)) {
            tt.updateFilteredValue(context, (TreeNode)null);
            tt.setValue((TreeNode)null);
            tt.setFirst(0);
            rppValue = (String)params.get(clientId + "_rppDD");
            if (rppValue != null && !"*".equals(rppValue)) {
                tt.setRows(Integer.parseInt(rppValue));
            }

            this.filter(context, tt, tt.getValue());
            this.sort(tt);
            this.encodeTbody(context, tt, tt.getValue(), true);
            if (tt.isMultiViewState()) {
                Map<String, FilterMeta> filterBy = tt.getFilterByAsMap();
                TreeTableState ts = tt.getMultiViewState(true);
                ts.setFilterBy(filterBy);
                if (tt.isPaginator()) {
                    ts.setFirst(tt.getFirst());
                    ts.setRows(tt.getRows());
                }
            }
        } else if (tt.isSortRequest(context)) {
            this.encodeSort(context, tt, root);
        } else if (tt.isRowEditRequest(context)) {
            this.encodeRowEdit(context, tt, root);
        } else if (tt.isCellEditRequest(context)) {
            this.encodeCellEdit(context, tt, root);
        } else if (tt.isPaginationRequest(context)) {
            tt.updatePaginationData(context);
            this.encodeNodeChildren(context, tt, root, root, tt.getFirst(), tt.getRows());
        } else {
            this.filter(context, tt, tt.getValue());
            this.sort(tt);
            this.encodeMarkup(context, tt);
            this.encodeScript(context, tt);
        }

    }

    protected void preRender(FacesContext context, TreeTable tt) {
        Map<String, FilterMeta> filterBy = tt.initFilterBy(context);
        if (tt.isFilterRequest(context)) {
            tt.updateFilterByValuesWithFilterRequest(context, filterBy);
        }

        tt.resetDynamicColumns();
        if (tt.isMultiViewState()) {
            tt.restoreMultiViewState();
        }

    }

    protected void encodeScript(FacesContext context, TreeTable tt) throws IOException {
        String selectionMode = tt.getSelectionMode();
        WidgetBuilder wb = this.getWidgetBuilder(context);
        wb.init("TreeTable", tt).attr("selectionMode", selectionMode, (String)null).attr("resizableColumns", tt.isResizableColumns(), false).attr("liveResize", tt.isLiveResize(), false).attr("scrollable", tt.isScrollable(), false).attr("scrollHeight", tt.getScrollHeight(), (String)null).attr("scrollWidth", tt.getScrollWidth(), (String)null).attr("nativeElements", tt.isNativeElements(), false).attr("expandMode", tt.getExpandMode(), "children").attr("disabledTextSelection", tt.isDisabledTextSelection(), true);
        if (tt.isStickyHeader()) {
            wb.attr("stickyHeader", true);
        }

        if (tt.isEditable()) {
            wb.attr("editable", true).attr("editMode", tt.getEditMode()).attr("saveOnCellBlur", tt.isSaveOnCellBlur(), true).attr("cellEditMode", tt.getCellEditMode(), "eager").attr("cellSeparator", tt.getCellSeparator(), (String)null).attr("editInitEvent", tt.getEditInitEvent());
        }

        if (tt.isFilteringEnabled()) {
            wb.attr("filter", true).attr("filterEvent", tt.getFilterEvent(), (String)null).attr("filterDelay", tt.getFilterDelay(), 2147483647);
        }

        if (tt.isSortingEnabled()) {
            wb.attr("sorting", true);
            if (tt.isMultiSort()) {
                wb.attr("multiSort", true).nativeAttr("sortMetaOrder", tt.getSortMetaAsString(), (String)null);
            }

            if (tt.isAllowUnsorting()) {
                wb.attr("allowUnsorting", true);
            }
        }

        if (tt.isPaginator()) {
            this.encodePaginatorConfig(context, tt, wb);
        }

        this.encodeClientBehaviors(context, tt);
        wb.finish();
    }

    protected void encodeMarkup(FacesContext context, TreeTable tt) throws IOException {
        ResponseWriter writer = context.getResponseWriter();
        String clientId = tt.getClientId(context);
        boolean scrollable = tt.isScrollable();
        boolean resizable = tt.isResizableColumns();
        TreeNode root = tt.getValue();
        boolean hasPaginator = tt.isPaginator();
        if (root == null) {
            throw new FacesException("treeTable's value must not be null. ClientId: " + clientId);
        } else if (!(root instanceof TreeNode)) {
            throw new FacesException("treeTable's value must be an instance of " + TreeNode.class.getName() + ". ClientId: " + clientId);
        } else {
            if (hasPaginator) {
                tt.calculateFirst();
            }

            if (root.getRowKey() == null) {
                root.setRowKey("root");
                tt.buildRowKeys(root);
                tt.initPreselection();
            }

            if (tt.isDefaultSort()) {
                this.sort(tt);
            }

            String containerClass = tt.isResizableColumns() ? "ui-treetable ui-treetable-resizable ui-widget" : "ui-treetable ui-widget";
            containerClass = scrollable ? containerClass + " " + "ui-treetable-scrollable" : containerClass;
            containerClass = tt.getStyleClass() == null ? containerClass : containerClass + " " + tt.getStyleClass();
            containerClass = tt.isShowUnselectableCheckbox() ? containerClass + " ui-treetable-checkbox-all" : containerClass;
            containerClass = tt.isShowGridlines() ? containerClass + " " + "ui-treetable-gridlines" : containerClass;
            containerClass = "small".equals(tt.getSize()) ? containerClass + " " + "ui-treetable-sm" : containerClass;
            containerClass = "large".equals(tt.getSize()) ? containerClass + " " + "ui-treetable-lg" : containerClass;
            writer.startElement("div", (UIComponent)null);
            writer.writeAttribute("id", clientId, "id");
            writer.writeAttribute("class", containerClass, (String)null);
            if (tt.getStyle() != null) {
                writer.writeAttribute("style", tt.getStyle(), (String)null);
            }

            if (scrollable) {
                this.encodeScrollableMarkup(context, tt, root);
            } else {
                this.encodeRegularMarkup(context, tt, root);
            }

            if (tt.getSelectionMode() != null) {
                this.encodeStateHolder(context, tt, clientId + "_selection", tt.getSelectedRowKeysAsString());
            }

            if (scrollable) {
                this.encodeStateHolder(context, tt, clientId + "_scrollState", tt.getScrollState());
            }

            if (resizable) {
                this.encodeStateHolder(context, tt, tt.getClientId(context) + "_resizableColumnState", tt.getColumnsWidthForClientSide());
            }

            writer.endElement("div");
        }
    }

    protected void encodeScrollableMarkup(FacesContext context, TreeTable tt, TreeNode root) throws IOException {
        String tableStyle = tt.getTableStyle();
        String tableStyleClass = tt.getTableStyleClass();
        boolean hasPaginator = tt.isPaginator();
        String paginatorPosition = tt.getPaginatorPosition();
        this.encodeScrollAreaStart(context, tt, "ui-widget-header ui-treetable-scrollable-header", "ui-treetable-scrollable-header-box", tableStyle, tableStyleClass, "header", "ui-treetable-header ui-widget-header ui-corner-top");
        if (hasPaginator && !"bottom".equalsIgnoreCase(paginatorPosition)) {
            this.encodePaginatorMarkup(context, tt, "top");
        }

        this.encodeThead(context, tt);
        this.encodeScrollAreaEnd(context);
        this.encodeScrollBody(context, tt, root, tableStyle, tableStyleClass);
        this.encodeScrollAreaStart(context, tt, "ui-widget-header ui-treetable-scrollable-footer", "ui-treetable-scrollable-footer-box", tableStyle, tableStyleClass, "footer", "ui-treetable-footer ui-widget-header ui-corner-bottom");
        this.encodeTfoot(context, tt);
        if (hasPaginator && !"top".equalsIgnoreCase(paginatorPosition)) {
            this.encodePaginatorMarkup(context, tt, "bottom");
        }

        this.encodeScrollAreaEnd(context);
    }

    protected void encodeScrollBody(FacesContext context, TreeTable tt, TreeNode root, String tableStyle, String tableStyleClass) throws IOException {
        ResponseWriter writer = context.getResponseWriter();
        String scrollHeight = tt.getScrollHeight();
        writer.startElement("div", (UIComponent)null);
        writer.writeAttribute("class", "ui-treetable-scrollable-body", (String)null);
        if (scrollHeight != null && scrollHeight.indexOf(37) == -1) {
            writer.writeAttribute("style", "height:" + scrollHeight + "px", (String)null);
        }

        writer.startElement("table", (UIComponent)null);
        writer.writeAttribute("role", "grid", (String)null);
        if (tableStyle != null) {
            writer.writeAttribute("style", tableStyle, (String)null);
        }

        if (tableStyleClass != null) {
            writer.writeAttribute("class", tableStyleClass, (String)null);
        }

        this.encodeTbody(context, tt, root, false);
        writer.endElement("table");
        writer.endElement("div");
    }

    protected void encodeScrollAreaStart(FacesContext context, TreeTable tt, String containerClass, String containerBoxClass, String tableStyle, String tableStyleClass, String facet, String facetClass) throws IOException {
        ResponseWriter writer = context.getResponseWriter();
        writer.startElement("div", (UIComponent)null);
        writer.writeAttribute("class", containerClass, (String)null);
        this.encodeFacet(context, tt, tt.getFacet(facet), facetClass);
        writer.startElement("div", (UIComponent)null);
        writer.writeAttribute("class", containerBoxClass, (String)null);
        writer.startElement("table", (UIComponent)null);
        writer.writeAttribute("role", "grid", (String)null);
        if (tableStyle != null) {
            writer.writeAttribute("style", tableStyle, (String)null);
        }

        if (tableStyleClass != null) {
            writer.writeAttribute("class", tableStyleClass, (String)null);
        }

    }

    protected void encodeScrollAreaEnd(FacesContext context) throws IOException {
        ResponseWriter writer = context.getResponseWriter();
        writer.endElement("table");
        writer.endElement("div");
        writer.endElement("div");
    }

    protected void encodeRegularMarkup(FacesContext context, TreeTable tt, TreeNode root) throws IOException {
        ResponseWriter writer = context.getResponseWriter();
        boolean hasPaginator = tt.isPaginator();
        String paginatorPosition = tt.getPaginatorPosition();
        this.encodeFacet(context, tt, tt.getFacet("header"), "ui-treetable-header ui-widget-header ui-corner-top");
        if (tt.isPaginator() && !"bottom".equalsIgnoreCase(paginatorPosition)) {
            this.encodePaginatorMarkup(context, tt, "top");
        }

        writer.startElement("table", tt);
        writer.writeAttribute("role", "treegrid", (String)null);
        if (tt.getTableStyle() != null) {
            writer.writeAttribute("style", tt.getTableStyle(), (String)null);
        }

        if (tt.getTableStyleClass() != null) {
            writer.writeAttribute("class", tt.getTableStyleClass(), (String)null);
        }

        this.encodeThead(context, tt);
        this.encodeTfoot(context, tt);
        this.encodeTbody(context, tt, root, false);
        writer.endElement("table");
        if (hasPaginator && !"top".equalsIgnoreCase(paginatorPosition)) {
            this.encodePaginatorMarkup(context, tt, "bottom");
        }

        this.encodeFacet(context, tt, tt.getFacet("footer"), "ui-treetable-footer ui-widget-header ui-corner-bottom");
    }

    protected void encodeThead(FacesContext context, TreeTable tt) throws IOException {
        ResponseWriter writer = context.getResponseWriter();
        ColumnGroup group = tt.getColumnGroup("header");
        String clientId = tt.getClientId(context);
        writer.startElement("thead", (UIComponent)null);
        writer.writeAttribute("id", clientId + "_head", (String)null);
        if (group != null && group.isRendered()) {
            context.getAttributes().put("org.primefaces.HELPER_RENDERER", "columnGroup");
            Iterator var16 = group.getChildren().iterator();

            label85:
            while(true) {
                label62:
                while(true) {
                    UIComponent child;
                    do {
                        if (!var16.hasNext()) {
                            context.getAttributes().remove("org.primefaces.HELPER_RENDERER");
                            break label85;
                        }

                        child = (UIComponent)var16.next();
                    } while(!child.isRendered());

                    if (child instanceof Row) {
                        Row headerRow = (Row)child;
                        String rowClass = headerRow.getStyleClass();
                        String rowStyle = headerRow.getStyle();
                        writer.startElement("tr", (UIComponent)null);
                        writer.writeAttribute("role", "row", (String)null);
                        if (rowClass != null) {
                            writer.writeAttribute("class", rowClass, (String)null);
                        }

                        if (rowStyle != null) {
                            writer.writeAttribute("style", rowStyle, (String)null);
                        }

                        Iterator var11 = headerRow.getChildren().iterator();

                        while(true) {
                            while(true) {
                                UIComponent headerRowChild;
                                do {
                                    if (!var11.hasNext()) {
                                        writer.endElement("tr");
                                        continue label62;
                                    }

                                    headerRowChild = (UIComponent)var11.next();
                                } while(!headerRowChild.isRendered());

                                if (headerRowChild instanceof Column) {
                                    this.encodeColumnHeader(context, tt, (Column)headerRowChild);
                                } else if (headerRowChild instanceof Columns) {
                                    List<DynamicColumn> dynamicColumns = ((Columns)headerRowChild).getDynamicColumns();
                                    Iterator var14 = dynamicColumns.iterator();

                                    while(var14.hasNext()) {
                                        DynamicColumn dynaColumn = (DynamicColumn)var14.next();
                                        dynaColumn.applyModel();
                                        this.encodeColumnHeader(context, tt, dynaColumn);
                                    }
                                } else {
                                    headerRowChild.encodeAll(context);
                                }
                            }
                        }
                    } else {
                        child.encodeAll(context);
                    }
                }
            }
        } else {
            writer.startElement("tr", (UIComponent)null);
            writer.writeAttribute("role", "row", (String)null);
            List<UIColumn> columns = tt.getColumns();

            for(int i = 0; i < columns.size(); ++i) {
                UIColumn column = (UIColumn)columns.get(i);
                if (column instanceof Column) {
                    this.encodeColumnHeader(context, tt, column);
                } else if (column instanceof DynamicColumn) {
                    DynamicColumn dynamicColumn = (DynamicColumn)column;
                    dynamicColumn.applyModel();
                    this.encodeColumnHeader(context, tt, dynamicColumn);
                }
            }

            writer.endElement("tr");
        }

        writer.endElement("thead");
    }

    protected void encodeTbody(FacesContext context, TreeTable tt, TreeNode root, boolean dataOnly) throws IOException {
        ResponseWriter writer = context.getResponseWriter();
        String clientId = tt.getClientId(context);
        boolean empty = root == null || root.getChildCount() == 0;
        UIComponent emptyFacet = tt.getFacet("emptyMessage");
        if (!dataOnly) {
            writer.startElement("tbody", (UIComponent)null);
            writer.writeAttribute("id", clientId + "_data", (String)null);
            writer.writeAttribute("class", "ui-treetable-data ui-widget-content", (String)null);
        }

        if (empty) {
            writer.startElement("tr", (UIComponent)null);
            writer.writeAttribute("class", "ui-widget-content ui-treetable-empty-message", (String)null);
            writer.startElement("td", (UIComponent)null);
            writer.writeAttribute("colspan", tt.getColumnsCount(), (String)null);
            if (ComponentUtils.shouldRenderFacet(emptyFacet)) {
                emptyFacet.encodeAll(context);
            } else {
                writer.writeText(tt.getEmptyMessage(), "emptyMessage");
            }

            writer.endElement("td");
            writer.endElement("tr");
        }

        if (root != null) {
            if (tt.isPaginator()) {
                int first = tt.getFirst();
                int rows = tt.getRows() == 0 ? tt.getRowCount() : tt.getRows();
                this.encodeNodeChildren(context, tt, root, root, first, rows);
            } else {
                this.encodeNodeChildren(context, tt, root, root);
            }
        }

        tt.setRowKey(root, (String)null);
        if (!dataOnly) {
            writer.endElement("tbody");
        }

    }

    protected void encodeNode(FacesContext context, TreeTable tt, TreeNode root, TreeNode treeNode) throws IOException {
        ResponseWriter writer = context.getResponseWriter();
        String rowKey = treeNode.getRowKey();
        String parentRowKey = treeNode.getParent().getRowKey();
        tt.setRowKey(root, rowKey);
        String icon = treeNode.isExpanded() ? "ui-treetable-toggler ui-icon ui-icon-triangle-1-s ui-c" : "ui-treetable-toggler ui-icon ui-icon-triangle-1-e ui-c";
        int depth = rowKey.split("_").length - 1;
        String selectionMode = tt.getSelectionMode();
        boolean selectionEnabled = selectionMode != null;
        boolean selectable = treeNode.isSelectable() && selectionEnabled;
        boolean checkboxSelection = selectionEnabled && "checkbox".equals(selectionMode);
        boolean selected = treeNode.isSelected();
        boolean partialSelected = treeNode.isPartialSelected();
        boolean nativeElements = tt.isNativeElements();
        List<UIColumn> columns = tt.getColumns();
        String rowStyleClass = selected ? "ui-widget-content ui-state-highlight ui-selected" : "ui-widget-content";
        rowStyleClass = selectable ? rowStyleClass + " " + "ui-treetable-selectable-node" : rowStyleClass;
        rowStyleClass = rowStyleClass + " " + treeNode.getType();
        rowStyleClass = rowStyleClass + " ui-node-level-" + rowKey.split("_").length;
        if (partialSelected) {
            rowStyleClass = rowStyleClass + " " + "ui-treetable-partialselected";
        }

        String userRowStyleClass = tt.getRowStyleClass();
        if (userRowStyleClass != null) {
            rowStyleClass = rowStyleClass + " " + userRowStyleClass;
        }

        if (tt.isEditingRow()) {
            rowStyleClass = rowStyleClass + " " + "ui-row-editing";
        }

        writer.startElement("tr", (UIComponent)null);
        writer.writeAttribute("id", tt.getClientId(context) + "_node_" + rowKey, (String)null);
        writer.writeAttribute("class", rowStyleClass, (String)null);
        writer.writeAttribute("role", "row", (String)null);
        writer.writeAttribute("aria-expanded", String.valueOf(treeNode.isExpanded()), (String)null);
        writer.writeAttribute("data-rk", rowKey, (String)null);
        if (parentRowKey != null) {
            writer.writeAttribute("data-prk", parentRowKey, (String)null);
        }

        if (selectionEnabled) {
            writer.writeAttribute("aria-selected", String.valueOf(selected), (String)null);
        }

        for(int i = 0; i < columns.size(); ++i) {
            UIColumn column = (UIColumn)columns.get(i);
            ColumnMeta columnMeta = (ColumnMeta)tt.getColumnMeta().get(column.getColumnKey(tt, rowKey));
            if (column.isDynamic()) {
                ((DynamicColumn)column).applyModel();
            }

            if (column.isRendered()) {
                boolean columnVisible = column.isVisible();
                if (columnMeta != null && columnMeta.getVisible() != null) {
                    columnVisible = columnMeta.getVisible();
                }

                String columnStyleClass = column.getStyleClass();
                String columnStyle = column.getStyle();
                int rowspan = column.getRowspan();
                int colspan = column.getColspan();
                int responsivePriority = column.getResponsivePriority();
                if (responsivePriority > 0) {
                    columnStyleClass = columnStyleClass == null ? "ui-column-p-" + responsivePriority : columnStyleClass + " ui-column-p-" + responsivePriority;
                }

                if (column.getCellEditor() != null) {
                    columnStyleClass = columnStyleClass == null ? "ui-editable-column" : "ui-editable-column " + columnStyleClass;
                }

                if (!columnVisible) {
                    columnStyleClass = columnStyleClass == null ? "ui-helper-hidden" : columnStyleClass + " " + "ui-helper-hidden";
                }

                writer.startElement("td", (UIComponent)null);
                writer.writeAttribute("role", "gridcell", (String)null);
                if (columnStyle != null) {
                    writer.writeAttribute("style", columnStyle, (String)null);
                }

                if (columnStyleClass != null) {
                    writer.writeAttribute("class", columnStyleClass, (String)null);
                }

                if (rowspan != 1) {
                    writer.writeAttribute("rowspan", rowspan, (String)null);
                }

                if (colspan != 1) {
                    writer.writeAttribute("colspan", colspan, (String)null);
                }

                if (i == 0) {
                    for(int j = 0; j < depth; ++j) {
                        writer.startElement("span", (UIComponent)null);
                        writer.writeAttribute("class", "ui-treetable-indent", (String)null);
                        writer.endElement("span");
                    }

                    writer.startElement("span", (UIComponent)null);
                    writer.writeAttribute("class", icon, (String)null);
                    if (treeNode.isLeaf()) {
                        writer.writeAttribute("style", "visibility:hidden", (String)null);
                    }

                    writer.endElement("span");
                    if (checkboxSelection) {
                        if (!nativeElements) {
                            RendererUtils.encodeCheckbox(context, selected, partialSelected, !selectable, "ui-selection");
                        } else {
                            this.renderNativeCheckbox(context, tt, selected, partialSelected);
                        }
                    }
                }

                column.renderChildren(context);
                writer.endElement("td");
            }
        }

        writer.endElement("tr");
        if (treeNode.isExpanded()) {
            this.encodeNodeChildren(context, tt, root, treeNode);
        }

    }

    public void encodeColumnHeader(FacesContext context, TreeTable tt, UIColumn column) throws IOException {
        if (column.isRendered()) {
            ColumnMeta columnMeta = (ColumnMeta)tt.getColumnMeta().get(column.getColumnKey());
            ResponseWriter writer = context.getResponseWriter();
            UIComponent header = column.getFacet("header");
            String headerText = column.getHeaderText();
            boolean columnVisible = column.isVisible();
            if (columnMeta != null && columnMeta.getVisible() != null) {
                columnVisible = columnMeta.getVisible();
            }

            int colspan = column.getColspan();
            int rowspan = column.getRowspan();
            boolean sortable = tt.isColumnSortable(context, column);
            boolean filterable = tt.isColumnFilterable(column);
            SortMeta sortMeta = null;
            String style = column.getStyle();
            String width = column.getWidth();
            String columnClass = sortable ? "ui-state-default ui-sortable-column" : "ui-state-default";
            columnClass = !columnVisible ? columnClass + " " + "ui-helper-hidden" : columnClass;
            columnClass = !column.isToggleable() ? columnClass + " " + "ui-static-column" : columnClass;
            String userColumnClass = column.getStyleClass();
            if (column.isResizable()) {
                columnClass = columnClass + " " + "ui-resizable-column";
            }

            if (userColumnClass != null) {
                columnClass = columnClass + " " + userColumnClass;
            }

            columnClass = filterable ? columnClass + " " + "ui-filter-column" : columnClass;
            if (sortable) {
                sortMeta = (SortMeta)tt.getSortByAsMap().get(column.getColumnKey());
                if (sortMeta.isActive()) {
                    columnClass = columnClass + " ui-state-active";
                }
            }

            int responsivePriority = column.getResponsivePriority();
            if (responsivePriority > 0) {
                columnClass = columnClass + " ui-column-p-" + responsivePriority;
            }

            String unit;
            if (width != null) {
                unit = this.endsWithLenghtUnit(width) ? "" : "px";
                if (style != null) {
                    style = style + ";width:" + width + unit;
                } else {
                    style = "width:" + width + unit;
                }
            }

            unit = this.getHeaderLabel(context, column);
            writer.startElement("th", (UIComponent)null);
            writer.writeAttribute("id", column.getContainerClientId(context), (String)null);
            writer.writeAttribute("class", columnClass, (String)null);
            writer.writeAttribute("role", "columnheader", (String)null);
            writer.writeAttribute("aria-label", unit, (String)null);
            if (style != null) {
                writer.writeAttribute("style", style, (String)null);
            }

            if (rowspan != 1) {
                writer.writeAttribute("rowspan", rowspan, (String)null);
            }

            if (colspan != 1) {
                writer.writeAttribute("colspan", colspan, (String)null);
            }

            writer.startElement("span", (UIComponent)null);
            writer.writeAttribute("class", "ui-column-title", (String)null);
            if (ComponentUtils.shouldRenderFacet(header)) {
                header.encodeAll(context);
            } else if (headerText != null) {
                writer.writeText(headerText, (String)null);
            }

            writer.endElement("span");
            if (sortable && sortMeta != null) {
                String sortIcon = this.resolveDefaultSortIcon(sortMeta);
                if (sortIcon != null) {
                    writer.startElement("span", (UIComponent)null);
                    writer.writeAttribute("class", sortIcon, (String)null);
                    writer.endElement("span");
                    if (tt.isMultiSort()) {
                        writer.startElement("span", (UIComponent)null);
                        writer.writeAttribute("class", "ui-sortable-column-badge ui-helper-hidden", (String)null);
                        writer.endElement("span");
                    }
                }
            }

            if (filterable) {
                this.encodeFilter(context, tt, column);
            }

            writer.endElement("th");
        }
    }

    protected String resolveDefaultSortIcon(SortMeta sortMeta) {
        SortOrder sortOrder = sortMeta.getOrder();
        String sortIcon = "ui-sortable-column-icon ui-icon ui-icon-carat-2-n-s";
        if (sortOrder.isAscending()) {
            sortIcon = "ui-sortable-column-icon ui-icon ui-icon ui-icon-carat-2-n-s ui-icon-triangle-1-n";
        } else if (sortOrder.isDescending()) {
            sortIcon = "ui-sortable-column-icon ui-icon ui-icon ui-icon-carat-2-n-s ui-icon-triangle-1-s";
        }

        return sortIcon;
    }

    protected void encodeFilter(FacesContext context, TreeTable tt, UIColumn column) throws IOException {
        ResponseWriter writer = context.getResponseWriter();
        UIComponent filterFacet = column.getFacet("filter");
        if (!ComponentUtils.shouldRenderFacet(filterFacet)) {
            String separator = String.valueOf(UINamingContainer.getSeparatorChar(context));
            boolean disableTabbing = tt.getScrollWidth() != null;
            String filterId = column.getContainerClientId(context) + separator + "filter";
            String filterStyleClass = column.getFilterStyleClass();
            Object filterValue = tt.getFilterValue(column);
            filterValue = filterValue == null ? "" : filterValue.toString();
            filterStyleClass = filterStyleClass == null ? "ui-column-filter ui-inputfield ui-inputtext ui-widget ui-state-default ui-corner-all" : "ui-column-filter ui-inputfield ui-inputtext ui-widget ui-state-default ui-corner-all " + filterStyleClass;
            writer.startElement("input", (UIComponent)null);
            writer.writeAttribute("id", filterId, (String)null);
            writer.writeAttribute("name", filterId, (String)null);
            writer.writeAttribute("class", filterStyleClass, (String)null);
            writer.writeAttribute("value", filterValue, (String)null);
            writer.writeAttribute("autocomplete", "off", (String)null);
            if (disableTabbing) {
                writer.writeAttribute("tabindex", "-1", (String)null);
            }

            if (column.getFilterStyle() != null) {
                writer.writeAttribute("style", column.getFilterStyle(), (String)null);
            }

            if (column.getFilterMaxLength() != 2147483647) {
                writer.writeAttribute("maxlength", column.getFilterMaxLength(), (String)null);
            }

            writer.endElement("input");
        } else {
            Object filterValue = tt.getFilterValue(column);
            if (filterValue != null) {
                ((ValueHolder)filterFacet).setValue(filterValue);
            }

            writer.startElement("div", (UIComponent)null);
            writer.writeAttribute("class", "ui-column-customfilter", (String)null);
            filterFacet.encodeAll(context);
            writer.endElement("div");
        }

    }

    protected void encodeNodeChildren(FacesContext context, TreeTable tt, TreeNode root, TreeNode treeNode) throws IOException {
        int childCount = treeNode.getChildCount();
        this.encodeNodeChildren(context, tt, root, treeNode, 0, childCount);
    }

    protected void encodeNodeChildren(FacesContext context, TreeTable tt, TreeNode root, TreeNode treeNode, int first, int size) throws IOException {
        if (size > 0) {
            List<TreeNode> children = treeNode.getChildren();
            int childCount = treeNode.getChildCount();
            int last = first + size;
            if (last > childCount) {
                last = childCount;
            }

            for(int i = first; i < last; ++i) {
                this.encodeNode(context, tt, root, (TreeNode)children.get(i));
            }
        }

    }

    protected void encodeFacet(FacesContext context, TreeTable tt, UIComponent facet, String styleClass) throws IOException {
        if (ComponentUtils.shouldRenderFacet(facet)) {
            ResponseWriter writer = context.getResponseWriter();
            writer.startElement("div", (UIComponent)null);
            writer.writeAttribute("class", styleClass, (String)null);
            facet.encodeAll(context);
            writer.endElement("div");
        }
    }

    protected void encodeTfoot(FacesContext context, TreeTable tt) throws IOException {
        ResponseWriter writer = context.getResponseWriter();
        ColumnGroup group = tt.getColumnGroup("footer");
        boolean hasFooterColumn = tt.hasFooterColumn();
        boolean shouldRenderFooter = hasFooterColumn || group != null;
        if (shouldRenderFooter) {
            writer.startElement("tfoot", (UIComponent)null);
            if (group != null && group.isRendered()) {
                context.getAttributes().put("org.primefaces.HELPER_RENDERER", "columnGroup");
                Iterator var17 = group.getChildren().iterator();

                label94:
                while(true) {
                    label71:
                    while(true) {
                        UIComponent child;
                        do {
                            if (!var17.hasNext()) {
                                context.getAttributes().remove("org.primefaces.HELPER_RENDERER");
                                break label94;
                            }

                            child = (UIComponent)var17.next();
                        } while(!child.isRendered());

                        if (child instanceof Row) {
                            Row footerRow = (Row)child;
                            String rowClass = footerRow.getStyleClass();
                            String rowStyle = footerRow.getStyle();
                            writer.startElement("tr", (UIComponent)null);
                            if (rowClass != null) {
                                writer.writeAttribute("class", rowClass, (String)null);
                            }

                            if (rowStyle != null) {
                                writer.writeAttribute("style", rowStyle, (String)null);
                            }

                            Iterator var12 = footerRow.getChildren().iterator();

                            while(true) {
                                while(true) {
                                    UIComponent footerRowChild;
                                    do {
                                        if (!var12.hasNext()) {
                                            writer.endElement("tr");
                                            continue label71;
                                        }

                                        footerRowChild = (UIComponent)var12.next();
                                    } while(!footerRowChild.isRendered());

                                    if (footerRowChild instanceof Column) {
                                        this.encodeColumnFooter(context, tt, (Column)footerRowChild);
                                    } else if (footerRowChild instanceof Columns) {
                                        List<DynamicColumn> dynamicColumns = ((Columns)footerRowChild).getDynamicColumns();
                                        Iterator var15 = dynamicColumns.iterator();

                                        while(var15.hasNext()) {
                                            DynamicColumn dynaColumn = (DynamicColumn)var15.next();
                                            dynaColumn.applyModel();
                                            this.encodeColumnFooter(context, tt, dynaColumn);
                                        }
                                    } else {
                                        footerRowChild.encodeAll(context);
                                    }
                                }
                            }
                        } else {
                            child.encodeAll(context);
                        }
                    }
                }
            } else if (hasFooterColumn) {
                writer.startElement("tr", (UIComponent)null);
                List<UIColumn> columns = tt.getColumns();

                for(int i = 0; i < columns.size(); ++i) {
                    UIColumn column = (UIColumn)columns.get(i);
                    if (column instanceof Column) {
                        this.encodeColumnFooter(context, tt, column);
                    } else if (column instanceof DynamicColumn) {
                        DynamicColumn dynamicColumn = (DynamicColumn)column;
                        dynamicColumn.applyModel();
                        this.encodeColumnFooter(context, tt, dynamicColumn);
                    }
                }

                writer.endElement("tr");
            }

            writer.endElement("tfoot");
        }
    }

    public void encodeColumnFooter(FacesContext context, TreeTable table, UIColumn column) throws IOException {
        if (column.isRendered()) {
            ResponseWriter writer = context.getResponseWriter();
            int colspan = column.getColspan();
            int rowspan = column.getRowspan();
            UIComponent footerFacet = column.getFacet("footer");
            String footerText = column.getFooterText();
            String style = column.getStyle();
            String columnStyleClass = column.getStyleClass();
            columnStyleClass = columnStyleClass == null ? "ui-state-default" : "ui-state-default " + columnStyleClass;
            int responsivePriority = column.getResponsivePriority();
            if (responsivePriority > 0) {
                columnStyleClass = columnStyleClass + " ui-column-p-" + responsivePriority;
            }

            writer.startElement("td", (UIComponent)null);
            writer.writeAttribute("class", columnStyleClass, (String)null);
            if (style != null) {
                writer.writeAttribute("style", style, (String)null);
            }

            if (rowspan != 1) {
                writer.writeAttribute("rowspan", rowspan, (String)null);
            }

            if (colspan != 1) {
                writer.writeAttribute("colspan", colspan, (String)null);
            }

            if (ComponentUtils.shouldRenderFacet(footerFacet)) {
                footerFacet.encodeAll(context);
            } else if (footerText != null) {
                writer.writeText(footerText, (String)null);
            }

            writer.endElement("td");
        }
    }

    public void encodeChildren(FacesContext facesContext, UIComponent component) throws IOException {
    }

    public boolean getRendersChildren() {
        return true;
    }

    private void encodeStateHolder(FacesContext context, TreeTable tt, String name, String value) throws IOException {
        ResponseWriter writer = context.getResponseWriter();
        writer.startElement("input", (UIComponent)null);
        writer.writeAttribute("id", name, (String)null);
        writer.writeAttribute("name", name, (String)null);
        writer.writeAttribute("type", "hidden", (String)null);
        writer.writeAttribute("value", value, (String)null);
        writer.endElement("input");
    }

    protected String resolveSortIcon(SortMeta sortMeta) {
        if (sortMeta == null) {
            return null;
        } else {
            SortOrder sortOrder = sortMeta.getOrder();
            if (sortOrder == SortOrder.ASCENDING) {
                return "ui-sortable-column-icon ui-icon ui-icon ui-icon-carat-2-n-s ui-icon-triangle-1-n";
            } else {
                return sortOrder == SortOrder.DESCENDING ? "ui-sortable-column-icon ui-icon ui-icon ui-icon-carat-2-n-s ui-icon-triangle-1-s" : null;
            }
        }
    }

    protected void decodeSort(FacesContext context, TreeTable tt) {
        Map<String, String> params = context.getExternalContext().getRequestParameterMap();
        String clientId = tt.getClientId(context);
        String sortKey = (String)params.get(clientId + "_sortKey");
        String sortDir = (String)params.get(clientId + "_sortDir");
        String[] sortKeys = sortKey.split(",");
        String[] sortOrders = sortDir.split(",");
        if (sortKeys.length != sortOrders.length) {
            throw new FacesException("sortKeys != sortDirs");
        } else {
            Map<String, SortMeta> sortByMap = tt.getSortByAsMap();
            Map<String, Integer> sortKeysIndexes = (Map)IntStream.range(0, sortKeys.length).boxed().collect(Collectors.toMap((i) -> {
                return sortKeys[i];
            }, (i) -> {
                return i;
            }));
            Iterator var11 = sortByMap.entrySet().iterator();

            while(var11.hasNext()) {
                Entry<String, SortMeta> entry = (Entry)var11.next();
                SortMeta sortBy = (SortMeta)entry.getValue();
                if (sortBy.getComponent() instanceof UIColumn) {
                    Integer index = (Integer)sortKeysIndexes.get(entry.getKey());
                    if (index != null) {
                        sortBy.setOrder(SortOrder.of(sortOrders[index]));
                        sortBy.setPriority(index);
                    } else {
                        sortBy.setOrder(SortOrder.UNSORTED);
                        sortBy.setPriority(SortMeta.MIN_PRIORITY);
                    }
                }
            }

        }
    }

    protected void encodeSort(FacesContext context, TreeTable tt, TreeNode root) throws IOException {
        this.sort(tt);
        this.encodeTbody(context, tt, root, true);
        if (tt.isMultiViewState()) {
            Map<String, SortMeta> sortMeta = tt.getSortByAsMap();
            TreeTableState ts = tt.getMultiViewState(true);
            ts.setSortBy(sortMeta);
            if (tt.isPaginator()) {
                ts.setFirst(tt.getFirst());
                ts.setRows(tt.getRows());
            }
        }

    }

    public void sort(TreeTable tt) {
        TreeNode root = tt.getValue();
        if (root != null) {
            Map<String, SortMeta> sortBy = tt.getSortByAsMap();
            if (!sortBy.isEmpty()) {
                Locale dataLocale = tt.resolveDataLocale();
                tt.forEachColumn((column) -> {
                    SortMeta meta = (SortMeta)sortBy.get(column.getColumnKey());
                    if (meta != null && meta.isActive()) {
                        if (column instanceof DynamicColumn) {
                            ((DynamicColumn)column).applyStatelessModel();
                        }

                        TreeUtils.sortNode(root, new TreeNodeComparator(meta.getSortBy(), tt.getVar(), meta.getOrder(), meta.getFunction(), meta.isCaseSensitiveSort(), dataLocale));
                        tt.updateRowKeys(root);
                        return true;
                    } else {
                        return true;
                    }
                });
                String selectedRowKeys = tt.getSelectedRowKeysAsString();
                if (selectedRowKeys != null) {
                    PrimeFaces.current().ajax().addCallbackParam("selection", selectedRowKeys);
                }

            }
        }
    }

    protected void renderNativeCheckbox(FacesContext context, TreeTable tt, boolean checked, boolean partialSelected) throws IOException {
        ResponseWriter writer = context.getResponseWriter();
        writer.startElement("input", (UIComponent)null);
        writer.writeAttribute("type", "checkbox", (String)null);
        writer.writeAttribute("name", tt.getContainerClientId(context) + "_checkbox", (String)null);
        if (checked) {
            writer.writeAttribute("checked", "checked", (String)null);
        }

        if (partialSelected) {
            writer.writeAttribute("class", "ui-treetable-indeterminate", (String)null);
        }

        writer.endElement("input");
    }

    public void encodeRowEdit(FacesContext context, TreeTable tt, TreeNode root) throws IOException {
        Map<String, String> params = context.getExternalContext().getRequestParameterMap();
        String clientId = tt.getClientId(context);
        String editedRowKey = (String)params.get(clientId + "_rowEditIndex");
        String action = (String)params.get(clientId + "_rowEditAction");
        tt.setRowKey(root, editedRowKey);
        TreeNode node = tt.getRowNode();
        if ("cancel".equals(action)) {
            VisitContext visitContext = null;
            Iterator var10 = tt.getColumns().iterator();

            while(var10.hasNext()) {
                UIColumn column = (UIColumn)var10.next();
                Iterator var12 = column.getChildren().iterator();

                while(var12.hasNext()) {
                    UIComponent grandkid = (UIComponent)var12.next();
                    if (grandkid instanceof CellEditor) {
                        UIComponent inputFacet = grandkid.getFacet("input");
                        if (inputFacet instanceof EditableValueHolder) {
                            ((EditableValueHolder)inputFacet).resetValue();
                        } else {
                            if (visitContext == null) {
                                visitContext = VisitContext.createVisitContext(context, (Collection)null, ComponentUtils.VISIT_HINTS_SKIP_UNRENDERED);
                            }

                            inputFacet.visitTree(visitContext, ResetInputVisitCallback.INSTANCE);
                        }
                    }
                }
            }
        }

        this.encodeNode(context, tt, root, node);
    }

    public void encodeCellEdit(FacesContext context, TreeTable tt, TreeNode root) throws IOException {
        Map<String, String> params = context.getExternalContext().getRequestParameterMap();
        String clientId = tt.getClientId(context);
        String[] cellInfo = ((String)params.get(clientId + "_cellInfo")).split(",");
        String rowKey = cellInfo[0];
        int cellIndex = Integer.parseInt(cellInfo[1]);
        int i = -1;
        UIColumn column = null;
        Iterator var11 = tt.getColumns().iterator();

        while(var11.hasNext()) {
            UIColumn col = (UIColumn)var11.next();
            if (col.isRendered()) {
                ++i;
                if (i == cellIndex) {
                    column = col;
                    break;
                }
            }
        }

        if (column == null) {
            throw new FacesException("No column found for cellIndex: " + cellIndex);
        } else {
            tt.setRowKey(root, rowKey);
            if (column.isDynamic()) {
                DynamicColumn dynamicColumn = (DynamicColumn)column;
                dynamicColumn.applyStatelessModel();
            }

            if (!tt.isCellEditCancelRequest(context) && !tt.isCellEditInitRequest(context)) {
                column.getCellEditor().getFacet("output").encodeAll(context);
            } else {
                column.getCellEditor().getFacet("input").encodeAll(context);
            }

            if (column.isDynamic()) {
                ((DynamicColumn)column).cleanStatelessModel();
            }

        }
    }

    public void filter(FacesContext context, TreeTable tt, TreeNode root) throws IOException {
        Map<String, FilterMeta> filterBy = tt.getFilterByAsMap();
        if (!filterBy.isEmpty()) {
            Locale filterLocale = LocaleUtils.getCurrentLocale(context);
            List<String> filteredRowKeys = tt.getFilteredRowKeys();
            filteredRowKeys.clear();
            this.collectFilteredRowKeys(context, tt, root, root, filterBy, filterLocale, filteredRowKeys);
            TreeNode filteredValue = this.cloneTreeNode(tt, root, root.getParent());
            this.createFilteredValueFromRowKeys(tt, root, filteredValue, filteredRowKeys);
            tt.updateFilteredValue(context, filteredValue);
            tt.setValue(filteredValue);
            tt.setRowKey(root, (String)null);
            if (tt.isPaginator()) {
                PrimeFaces.current().ajax().addCallbackParam("totalRecords", filteredValue.getChildCount());
            }

            if (tt.getSelectedRowKeysAsString() != null) {
                PrimeFaces.current().ajax().addCallbackParam("selection", tt.getSelectedRowKeysAsString());
            }

        }
    }

    protected void collectFilteredRowKeys(FacesContext context, TreeTable tt, TreeNode root, TreeNode node, Map<String, FilterMeta> filterBy, Locale filterLocale, List<String> filteredRowKeys) throws IOException {
        ELContext elContext = context.getELContext();
        FilterMeta globalFilter = (FilterMeta)filterBy.get("globalFilter");
        boolean hasGlobalFilterFunction = globalFilter != null && globalFilter.getConstraint() instanceof FunctionFilterConstraint;
        int childCount = node.getChildCount();
        AtomicBoolean localMatch = new AtomicBoolean();
        AtomicBoolean globalMatch = new AtomicBoolean();

        for(int i = 0; i < childCount; ++i) {
            TreeNode childNode = (TreeNode)node.getChildren().get(i);
            String rowKey = childNode.getRowKey();
            tt.setRowKey(root, rowKey);
            localMatch.set(true);
            globalMatch.set(false);
            if (hasGlobalFilterFunction) {
                globalMatch.set(globalFilter.getConstraint().isMatching(context, childNode, globalFilter.getFilterValue(), filterLocale));
            }

            tt.forEachColumn((column) -> {
                FilterMeta filter = (FilterMeta)filterBy.get(column.getColumnKey(tt, rowKey));
                if (filter != null && !filter.isGlobalFilter()) {
                    filter.setColumn(column);
                    Object columnValue = filter.getLocalValue(elContext);
                    FilterConstraint constraint;
                    Object filterValue;
                    if (globalFilter != null && globalFilter.isActive() && !globalMatch.get() && !hasGlobalFilterFunction) {
                        constraint = globalFilter.getConstraint();
                        filterValue = globalFilter.getFilterValue();
                        globalMatch.set(constraint.isMatching(context, columnValue, filterValue, filterLocale));
                    }

                    if (!filter.isActive()) {
                        return true;
                    } else {
                        constraint = filter.getConstraint();
                        filterValue = filter.getFilterValue();
                        localMatch.set(constraint.isMatching(context, columnValue, filterValue, filterLocale));
                        return localMatch.get();
                    }
                } else {
                    return true;
                }
            });
            boolean matches = localMatch.get();
            if (globalFilter != null && globalFilter.isActive()) {
                matches = matches && globalMatch.get();
            }

            if (matches) {
                filteredRowKeys.add(rowKey);
            }

            this.collectFilteredRowKeys(context, tt, root, childNode, filterBy, filterLocale, filteredRowKeys);
        }

    }

    private void createFilteredValueFromRowKeys(TreeTable tt, TreeNode node, TreeNode filteredNode, List<String> filteredRowKeys) {
        int childCount = node.getChildCount();

        label34:
        for(int i = 0; i < childCount; ++i) {
            TreeNode childNode = (TreeNode)node.getChildren().get(i);
            String rowKeyOfChildNode = childNode.getRowKey();
            Iterator var9 = filteredRowKeys.iterator();

            String rk;
            do {
                if (!var9.hasNext()) {
                    continue label34;
                }

                rk = (String)var9.next();
            } while(!rk.equals(rowKeyOfChildNode) && !rk.startsWith(rowKeyOfChildNode + "_") && !rowKeyOfChildNode.startsWith(rk + "_"));

            TreeNode newNode = this.cloneTreeNode(tt, childNode, filteredNode);
            if (rk.startsWith(rowKeyOfChildNode + "_")) {
                newNode.setExpanded(true);
            }

            this.createFilteredValueFromRowKeys(tt, childNode, newNode, filteredRowKeys);
        }

    }

    protected TreeNode cloneTreeNode(TreeTable tt, TreeNode node, TreeNode parent) {
        TreeNode clone = null;
        if (CheckboxTreeNode.class.equals(node.getClass())) {
            clone = new CheckboxTreeNode(node.getType(), node.getData(), parent);
        } else if (DefaultTreeNode.class.equals(node.getClass())) {
            clone = new DefaultTreeNode(node.getType(), node.getData(), parent);
        }

        if (clone == null && tt.isCloneOnFilter()) {
            if (node instanceof Cloneable) {
                try {
                    Method cloneMethod = node.getClass().getMethod("clone");
                    if (cloneMethod != null) {
                        cloneMethod.setAccessible(true);
                        clone = (TreeNode)cloneMethod.invoke(node);
                    }
                } catch (NoSuchMethodException var10) {
                    LOGGER.warning(node.getClass().getName() + " declares Cloneable but no clone() method found!");
                } catch (IllegalAccessException | InvocationTargetException var11) {
                    LOGGER.warning(node.getClass().getName() + "#clone() not accessible!");
                }
            } else {
                Constructor ctor;
                try {
                    ctor = node.getClass().getConstructor(node.getClass());
                    clone = (TreeNode)ctor.newInstance(node);
                } catch (NoSuchMethodException var8) {
                } catch (IllegalAccessException | InstantiationException | InvocationTargetException var9) {
                    LOGGER.warning("Could not clone " + node.getClass().getName() + " via public " + node.getClass().getSimpleName() + "() constructor!");
                }

                if (clone == null) {
                    try {
                        ctor = node.getClass().getConstructor(String.class, Object.class, TreeNode.class);
                        clone = (TreeNode)ctor.newInstance(node.getType(), node.getData(), parent);
                    } catch (NoSuchMethodException var6) {
                    } catch (IllegalAccessException | InstantiationException | InvocationTargetException var7) {
                        LOGGER.warning("Could not clone " + node.getClass().getName() + " via public " + node.getClass().getSimpleName() + "(String type, Object data, TreeNode parent) constructor!");
                    }
                }
            }
        }

        if (clone == null) {
            if (node instanceof CheckboxTreeNode) {
                clone = new CheckboxTreeNode(node.getType(), node.getData(), parent);
            } else {
                clone = new DefaultTreeNode(node.getType(), node.getData(), parent);
            }
        }

        ((TreeNode)clone).setSelected(node.isSelected());
        ((TreeNode)clone).setExpanded(node.isExpanded());
        ((TreeNode)clone).setSelectable(node.isSelectable());
        return (TreeNode)clone;
    }
}
