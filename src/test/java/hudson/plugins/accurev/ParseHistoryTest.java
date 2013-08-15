package hudson.plugins.accurev;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ParseHistoryTest {
	
	@Test
	public void filterCollectionOfFiles_validTrans_noFilter() {
		List<String> files = Arrays.asList( 
				new String[]{"foo/bar/baz.java", "derp/herp/waffles.properties", "lots/of/changes/in/everywhere/Things.java"});
		List<String> filters = Arrays.asList( new String[]{} );
		
		ParseHistory parser = new ParseHistory(filters);
		assertTrue("This should be valid, no filters = no reason to reject transaction", parser.isTransactionAcceptableThroughFilter(files));
		assertTrue("This should be valid, null filter = no reason to reject transaction", new ParseHistory(null).isTransactionAcceptableThroughFilter(files));	
	}
	
	@Test
	public void filterCollectionOfFiles_validTrans_noFiles() {
		List<String> files = Arrays.asList( new String[]{});
		List<String> filters = Arrays.asList( new String[]{".*\\.java"} );
		
		ParseHistory parser = new ParseHistory(filters);
		assertTrue("This should be valid, no files = something like a chstream which can effect lots w/o direct output, err on the side of caution", 
				parser.isTransactionAcceptableThroughFilter(files));	
		}
	
	
	@Test
	public void filterCollectionOfFiles_validTrans_filtered() {
		List<String> files = Arrays.asList( 
				new String[]{"foo/bar/baz.java", "derp/herp/waffles.properties", "lots/of/changes/in/everywhere/Things.java"});
		List<String> filters = Arrays.asList( new String[]{".*\\.java"} );  //properties file will still match.
		
		ParseHistory parser = new ParseHistory(filters);
		assertTrue("This should be valid", parser.isTransactionAcceptableThroughFilter(files));
	}
	
	@Test
	public void filterCollectionOfFiles_validTrans_multipleFilters() {
		List<String> files = Arrays.asList( 
				new String[]{"foo/bar/baz.java", "derp/herp/waffles.properties", "lots/of/changes/in/everywhere/Things.java"});
		List<String> filters = Arrays.asList( new String[]{".*derp.*", ".*changes.*"} );  //baz.java still matches
		
		ParseHistory parser = new ParseHistory(filters);
		assertTrue("This should be valid", parser.isTransactionAcceptableThroughFilter(files));
	}
	
	@Test
	public void filterCollectionOfFiles_invalidTrans_multipleFilters() {
		List<String> files = Arrays.asList( 
				new String[]{"foo/bar/baz.java", "derp/herp/waffles.properties", "lots/of/changes/in/everywhere/Things.java"});
		List<String> filters = Arrays.asList( new String[]{".*\\.java",".*\\.properties"} );  //all gone
		
		ParseHistory parser = new ParseHistory(filters);
		assertFalse("This should be rejected", parser.isTransactionAcceptableThroughFilter(files));
	}
}
