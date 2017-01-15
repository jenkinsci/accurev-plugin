package hudson.plugins.accurev.config.AccurevPluginConfig

f = namespace(lib.FormTagLib)

f.section(title: desciptor.displayName) {
    f.entry(title: _("Accurev Servers")) {
        f.repeatableHeteroProperty(
                field: "configs",
                hasHeader: "true",
                addCaption: _("Add GitHub Server")
        )
    }
}
