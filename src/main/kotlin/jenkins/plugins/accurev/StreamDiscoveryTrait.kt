package jenkins.plugins.accurev

import jenkins.scm.api.SCMHeadCategory
import jenkins.scm.api.SCMSource
import jenkins.scm.api.trait.SCMSourceContext
import jenkins.scm.api.trait.SCMSourceTrait
import jenkins.scm.api.trait.SCMSourceTraitDescriptor

class StreamDiscoveryTrait : SCMSourceTrait() {
    override fun decorateContext(context: SCMSourceContext<*, *>?) {
        val ctx = context as AccurevSCMSourceContext?
        ctx?.wantStreams(true)
    }

    override fun includeCategory(category: SCMHeadCategory): Boolean {
        return category.isUncategorized
    }

    class DescriptorImpl : SCMSourceTraitDescriptor() {

        override fun getDisplayName(): String {
            return Messages.StreamDiscoveryTrait_DisplayName()
        }

        override fun getContextClass(): Class<out SCMSourceContext<*, *>> {
            return AccurevSCMSourceContext::class.java
        }

        override fun getSourceClass(): Class<out SCMSource> {
            return AccurevSCMSource::class.java
        }
    }
}
