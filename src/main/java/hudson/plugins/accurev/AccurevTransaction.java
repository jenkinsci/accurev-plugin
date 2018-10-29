package hudson.plugins.accurev;

import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.EditType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/** One commit. */
@ExportedBean(defaultVisibility = 999)
public final class AccurevTransaction extends ChangeLogSet.Entry {

  private static final String FIELD_SEPARATOR = ", ";
  private static final String EQ = "=";
  private final List<String> affectedPaths = new ArrayList<>();
  private final List<String> affectedRawPaths = new ArrayList<>();
  private final List<String> fileRevisions = new ArrayList<>();
  //    private String revision;
  private User author;
  private Date date;
  private String msg;
  private String action;
  private String id;
  private String issueNum;
  private String webuiURLforTrans;
  private String webuiURLforIssue;

  @Exported
  public String getIssueNum() {
    return issueNum;
  }

  public void setIssueNum(String issueNum) {
    this.issueNum = issueNum;
  }

  @Exported
  public String getWebuiURLforTrans() {
    return webuiURLforTrans;
  }

  public void setWebuiURLforTrans(String webuiURLforTrans) {
    this.webuiURLforTrans = webuiURLforTrans;
  }

  /*@Exported
  public String getRevision() {
      return revision;
  }*/

  @Exported
  public String getWebuiURLforIssue() {
    return webuiURLforIssue;
  }

  public void setWebuiURLforIssue(String webuiURLforIssue) {
    this.webuiURLforIssue = webuiURLforIssue;
  }

  public void addFileRevision(String revision) {
    fileRevisions.add(revision);
  }

  @Exported
  public User getAuthor() {
    return author;
  }

  /**
   * Returns a set of paths in the workspace that was affected by this change. Contains string like
   * 'foo/bar/zot'. No leading/trailing '/', and separator must be normalized to '/'.
   *
   * @return never null.
   */
  @Exported
  public Collection<String> getAffectedPaths() {
    return affectedPaths;
  }

  public List<String> getAffectedRawPaths() {
    return affectedRawPaths;
  }

  @Exported
  public Collection<String> getFileRevisions() {
    return fileRevisions;
  }

  @Exported
  public String getUser() { // digester wants read/write property, even though it never reads. Duh.
    return author.getDisplayName();
  }

  public void setUser(String author) {
    this.author = User.getById(author, false);
  }

  @Exported
  public Date getDate() {
    return (Date) date.clone();
  }

  public void setDate(Date date) {
    this.date = (Date) date.clone();
  }

  @Exported
  public String getMsg() {
    return (StringUtils.isEmpty(msg) ? "" : msg);
  }

  public void setMsg(String msg) {
    this.msg = msg;
  }

  protected void setParent(ChangeLogSet parent) {
    super.setParent(parent); // Needed since parent method is protected
  }

  @Exported
  public EditType getEditType() {
    if (action.equals("promote")) {
      return EditType.EDIT;
    }
    if (action.equals("defunct")) {
      return EditType.DELETE;
    }
    if (action.equals("chstream")) {
      return EditType.EDIT;
    }
    if (action.equals("add")) {
      return EditType.ADD;
    }
    return EditType.EDIT;
  }

  public void addAffectedPath(String path) {
    affectedPaths.add(path);
  }

  public void addAffectedRawPath(String path) {
    affectedRawPaths.add(path);
  }

  /**
   * Getter for action Enables accurate filtering by AccuRev transaction type since the metod
   * getEditType censors the actual type.
   *
   * @return transaction type of the AccuRev transaction
   */
  @Exported
  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
    if ("chstream".equals(action) && StringUtils.isEmpty(msg)) {
      msg = "Changed Parent Stream";
    }
  }

  /**
   * Getter for id Enables logging with AccuRev transaction id
   *
   * @return transaction id of the AccuRev transaction
   */
  @Exported
  public String getId() {
    return id;
  }

  /**
   * Setter for id
   *
   * @param id transaction id of the AccuRev transaction
   */
  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return '['
        + //
        "id"
        + EQ
        + id
        + //
        FIELD_SEPARATOR
        + //
        "date"
        + EQ
        + date
        + //
        FIELD_SEPARATOR
        + //
        "author"
        + EQ
        + author
        + //
        FIELD_SEPARATOR
        + //
        "action"
        + EQ
        + action
        + //
        FIELD_SEPARATOR
        + //
        "msg"
        + EQ
        + getMsg()
        + //
        ']';
  }
}
