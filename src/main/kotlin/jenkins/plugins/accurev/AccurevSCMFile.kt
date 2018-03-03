package jenkins.plugins.accurev

import jenkins.scm.api.SCMFile
import java.io.InputStream

class AccurevSCMFile: SCMFile {

    private val fs: AccurevSCMFileSystem

    constructor(fs: AccurevSCMFileSystem) {
        this.fs = fs
    }

    constructor(fs: AccurevSCMFileSystem, parent: AccurevSCMFile, name: String) : super(parent, name) {
        this.fs = fs
    }

    override fun newChild(name: String, assumeIsDirectory: Boolean): SCMFile {
        return AccurevSCMFile(fs, this, name)
    }

    override fun children(): MutableIterable<SCMFile> {
    }

    override fun content(): InputStream {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun lastModified(): Long {
        return fs.lastModified()
    }

    override fun type(): Type {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}
