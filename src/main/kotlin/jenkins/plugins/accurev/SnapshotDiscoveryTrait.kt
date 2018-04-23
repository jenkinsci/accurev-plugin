package jenkins.plugins.accurev

import jenkins.scm.api.SCMHeadCategory
import jenkins.scm.api.SCMSource
import jenkins.scm.api.trait.SCMSourceContext
import jenkins.scm.api.trait.SCMSourceTrait
import jenkins.scm.api.trait.SCMSourceTraitDescriptor
import jenkins.scm.impl.TagSCMHeadCategory

class SnapshotDiscoveryTrait : SCMSourceTrait() {
    override fun decorateContext(context: SCMSourceContext<*, *>?) {
        val ctx = context as AccurevSCMSourceContext?
        ctx!!.wantSnapshots(true)
    }

    override fun includeCategory(category: SCMHeadCategory): Boolean {
        return category is TagSCMHeadCategory
    }

    class DescriptorImpl : SCMSourceTraitDescriptor() {

        override fun getDisplayName(): String {
            return Messages.SnapshotDiscoveryTrait_DisplayName()
        }

        override fun getContextClass(): Class<out SCMSourceContext<*, *>> {
            return AccurevSCMSourceContext::class.java
        }

        override fun getSourceClass(): Class<out SCMSource> {
            return AccurevSCMSource::class.java
        }
    }
}
