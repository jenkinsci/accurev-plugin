package hudson.plugins.accurev.AccurevSCM

import lib.FormTagLib

def f = namespace(FormTagLib)

f.entry(title: _("Repositories"), field: "userRemoteConfigs") {
    f.repeatableProperty(field: "userRemoteConfigs", minimum: 1, noAddButton: true)
}
if (descriptor.showAccurevToolOptions()) {
    f.entry(field: "accurevTool", title: _("Accurev executable")) {
        f.select()
    }
}
f.entry(title: _("Addtional Behaviours")) {
    f.repeatableHeteroProperty(field: "extensions", hasHeader: true, oneEach: true)
}
