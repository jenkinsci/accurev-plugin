package hudson.plugins.accurev.AccurevSCM.AccurevServer

import lib.CredentialsTagLib
import lib.FormTagLib

def f = namespace(FormTagLib)
def c = namespace(CredentialsTagLib)

f.entry(field: "name", title: _("Name")) {
    f.textbox()
}
f.entry(field: "host", title: _("Host")) {
    f.textbox()
}
f.entry(field: "port", title: _("Port")) {
    f.number(clazz: "number", min: 0, max: 65535, step: 1, default: 5050)
}
f.entry(field: "credentialsId", title: _("Credentials")) {
    c.select()
}
f.advanced {
    f.entry(field: "syncOperations", title: _("Synchronize AccuRev CLI Operations"), help: "/plugin/accurev/help/sync-operations.html") {
        f.checkbox()
    }
    f.entry(field: "minimiseLogins", title: _("Minimise AccuRev Login Operations"), help: "/plugin/accurev/help/minimise-login-operations.html") {
        f.checkbox()
    }
    f.entry(field: "useNonexpiringLogin", title: _("Use Non-expiring Login"), help: "/plugin/accurev/help/use-nonexpiring-login.html") {
        f.checkbox()
    }
    f.entry(field: "useRestrictedShowStreams", title: _("Show one stream at a time"), help: "/plugin/accurev/help/use-restricted-show-streams.html") {
        f.checkbox()
    }
    f.entry(field: "useColor", title: _("Enable reset Color"), help: "/plugin/accurev/help/use-color.html") {
        f.checkbox()
    }
    f.entry(field: "usePromoteListen", title: _("Enable Post Promote Listener"), help: "/plugin/accurev/help/use-promote-listen.html") {
        f.checkbox()
    }
    f.entry(field: "uuid", title: _("ID")) {
        f.textbox(disabled: true)
    }
    
     f.entry(field: "enablePlugin", title: "Disable Plugin for this server ", help: "/plugin/accurev/help/poll-on-master.html") {
        f.checkbox()
    }
}
