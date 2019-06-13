package jenkins.plugins.accurev.AccurevTool

/**
 * Initialized by josep on 21-01-2017.
 */

f = namespace("/lib/form")

f.entry(field: "name", title: _("Name")) {
  f.textbox()
}
f.entry(field: "home", title: _("Path to AccuRev executable")) {
  f.textbox()
}