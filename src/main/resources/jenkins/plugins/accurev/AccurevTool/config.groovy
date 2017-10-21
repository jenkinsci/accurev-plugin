package jenkins.plugins.accurev.AccurevTool

import lib.FormTagLib

f = namespace(FormTagLib)

f.entry(field: "name", title: _("Name")) {
    f.textbox()
}
f.entry(field: "home", title: _("Path to Accurev executable")) {
    f.textbox()
}
