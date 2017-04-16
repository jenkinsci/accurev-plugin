package hudson.plugins.accurev.AccurevSCM

import lib.FormTagLib

def f = namespace(FormTagLib)

f.section(title: "AccuRev") {
    f.entry(field: "usePollOnMaster", title: "Poll on master", help: "/plugin/accurev/help/poll-on-master.html") {
        f.checkbox()
    }
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
}
