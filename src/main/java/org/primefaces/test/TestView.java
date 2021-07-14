package org.primefaces.test;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import javax.annotation.PostConstruct;
import javax.faces.view.ViewScoped;
import javax.inject.Named;

import lombok.Data;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;

@Data
@Named
@ViewScoped
public class TestView implements Serializable {
    
    private String string;
    private Integer integer;
    private BigDecimal decimal;
    private LocalDateTime localDateTime;

    private TreeNode baseTreeNode;
    private TreeNode[] selectedTreeNodes;
    
    @PostConstruct  
    public void init() {
        string = "TreeTable bug demo";
        baseTreeNode= new DefaultTreeNode(new Selection("baseNode"));
        TreeNode unselTreeNode= new DefaultTreeNode(new Selection("unselectable"), baseTreeNode);
        unselTreeNode.setSelectable(false);
        TreeNode selectable= new DefaultTreeNode(new Selection("selectable"), unselTreeNode);

    }

}
