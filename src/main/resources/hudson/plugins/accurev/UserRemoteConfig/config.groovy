package hudson.plugins.accurev.UserRemoteConfig

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
f.entry(field: "localDir", title: _("Local directory"), help: "/plugin/accurev/help/project/localdirectory.html") {
    f.textbox(default: ".")
}

f.entry {
    div(align: "right") {
        input(type: "button", value: _("Add Repository"), class: "repeatable-add show-if-last")
        input(type: "button", value: _("Delete Repository"), class: "repeatable-delete show-if-not-only")
    }
}
