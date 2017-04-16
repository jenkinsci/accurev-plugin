package hudson.plugins.accurev.AccurevSCM

import lib.CredentialsTagLib
import lib.FormTagLib

def f = namespace(FormTagLib)
def c = namespace(CredentialsTagLib)

f.entry(field: "url", title: _("Accurev Server URL"), help: "/plugin/accurev/help/project/server.html") {
    f.textbox()
}
f.entry(field: "credentialsId", title: _("Credentials")) {
    c.select()
}
f.entry(field: "depot", title: _("Depot"), help: "/plugin/accurev/help/project/depot.html") {
    f.textbox()
}
f.entry(field: "stream", title: _("Stream/Workspace"), help: "/plugin/accurev/help/project/stream.html") {
    f.textbox()
}
if (descriptor.showAccurevToolOptions()) {
    f.entry(field: "accurevTool", title: _("Accurev executable")) {
        f.select()
    }
}
f.entry(title: _("Addtional Behaviours")) {
    f.repeatableHeteroProperty(field: "extensions", hasHeader: true, oneEach: true)
}
// Extensions:
// SCM API provide inclusion, exclusion
// Sub path populate
// Ignore Parent
// Skip populating
// Snapshot
// Use Accurev Workspace
