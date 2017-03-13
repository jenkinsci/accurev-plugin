package hudson.plugins.accurev.AccurevSCM

def f = namespace(lib.FormTagLib);

f.section(title: "AccuRev") {
    f.entry(field: "usePollOnMaster", title: "Poll on master", help: "/plugin/accurev/help/poll-on-master.html") {
        f.checkbox()
    }
    f.entry(title: _("AccuRev Servers")) {
        f.repeatableHeteroProperty(
            field: "servers",
            hasHeader: "true",
            addCaption: _("Add AccuRev Server")
        )
    }
}
