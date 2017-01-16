package hudson.plugins.accurev.config.AccurevServerConfig

def f = namespace(lib.FormTagLib);
def c = namespace(lib.CredentialsTagLib)

f.entry(title: _("Name"), field: "name") {
    f.textbox()
}
f.entry(title: _("Host"), field: "host") {
    f.textbox()
}
f.entry(title: _("Port"), field: "port") {
    f.number(clazz:"number",min:0,max:65535,step:1,default:5050)
}
f.entry(title: _("Credentials"), field: "credentialsId") {
    c.select()
}
f.advanced {
    f.entry(title: _("Minimise AccuRev Login Operations"), field: "useMinimiseLogin") {
        f.checkbox()
    }
    f.entry(title: _("Enable Post Promote Listener"), field: "usePromoteListener") {
        f.checkbox()
    }
    f.entry(title: _("ID"), field: "id") {
        f.textbox(disabled: true)
    }
}