package hudson.plugins.accurev.config.AccurevPluginConfig

def f = namespace(lib.FormTagLib);

f.section(title: "AccuRev") {
    f.entry(title: _("AccuRev Servers")) {
        f.repeatableHeteroProperty(
                field: "configs",
                hasHeader: "true",
                addCaption: _("Add AccuRev Server")
        )
    }
}
