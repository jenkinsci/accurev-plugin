package hudson.plugins.accurev;

import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.EditType;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * One commit.
 */
@ExportedBean(defaultVisibility=999)
public final class AccurevTransaction extends ChangeLogSet.Entry {
    private String revision;
    private User author;
    private Date date;
    private String msg;
    private String action;
    private List<String> affectedPaths = new ArrayList<String>();
    private int id;

    @Exported
    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    @Exported
    public User getAuthor() {
        return author;
    }

    /**
     * Returns a set of paths in the workspace that was
     * affected by this change.
     * <p/>
     * <p/>
     * Contains string like 'foo/bar/zot'. No leading/trailing '/',
     * and separator must be normalized to '/'.
     *
     * @return never null.
     */
    @Exported
    public Collection<String> getAffectedPaths() {
        return affectedPaths;
    }

    public void setUser(String author) {
        this.author = User.get(author);
    }

    @Exported
    public String getUser() {// digester wants read/write property, even though it never reads. Duh.
        return author.getDisplayName();
    }

    @Exported
    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    @Exported
    public String getMsg() {
        return (null == msg ? "" : msg);
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public void setAction(String action) {
        this.action = action;
        if ("chstream".equals(action) && (msg == null || "".equals(msg))) {
            msg = "Changed Parent Stream";
        }
    }

    protected void setParent(ChangeLogSet parent) {
        super.setParent(parent);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Exported
    public EditType getEditType() {
        if (action.equals("promote"))
            return EditType.EDIT;
        if (action.equals("defunct"))
            return EditType.DELETE;
        if (action.equals("chstream"))
            return EditType.EDIT;
        if (action.equals("add"))
            return EditType.ADD;
        return EditType.EDIT;
    }

    public void addAffectedPath(String path) {
        affectedPaths.add(path);
    }
    /**
     * Getter for action
     * Enables accurate filtering by AccuRev transaction type since the metod getEditType censors the actual type.
     * @return transaction type of the AccuRev transaction
     */
    @Exported
    public String getAction() {
        return action;
    }

    /**
     * Getter for id
     * Enables logging with AccuRev transaction id
     * @return transaction id of the AccuRev transaction
     */
    @Exported
    public int getId() {
        return id;
    }

    /**
     * Setter for id
     * @param id transaction id of the AccuRev transaction
     */
    public void setId(int id) {
        this.id = id;
    }

    private static final String FIELD_SEPARATOR = ", ";
    private static final String EQ = "=";

    @Override
    public String toString() {
        return '[' + //
                "id" + EQ + id + //
                FIELD_SEPARATOR + //
                "date" + EQ + date + //
                FIELD_SEPARATOR + //
                "author" + EQ + author + //
                FIELD_SEPARATOR + //
                "action" + EQ + action + //
                FIELD_SEPARATOR + //
                "msg" + EQ + getMsg() + //
                ']';
    }
}
