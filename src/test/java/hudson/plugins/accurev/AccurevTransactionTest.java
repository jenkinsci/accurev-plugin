package hudson.plugins.accurev;

import static org.junit.Assert.*;
import hudson.MarkupText;
import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogAnnotator;
import hudson.scm.ChangeLogSet.Entry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AccurevTransactionTest {
	
	AccurevTransaction trans;
	
	@Before
	public void setup(){
		trans = new AccurevTransaction();
	}
	
	@After
	public void teardown(){
		trans = null;
	}
	
	@Test
	public void neverReturnsNullMsg(){
		//even if this somehow gets null stored into it, never return it back.  screws with other plugins
		trans.setMsg(null);
		assertNotNull(trans.getMsg());
		assertEquals("", trans.getMsg());
	}
}
