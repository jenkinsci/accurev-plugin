package hudson.plugins.accurev;

import static org.junit.Assert.*;

import java.lang.reflect.Method;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AccurevLauncherTest {
	
	AccurevLauncher launcher;
	Method ourMethod;
	
	@Before
	public void setup() throws Exception {
		launcher = new AccurevLauncher();
		//Dont want to bother with trying to mock up all the data that a testHarness really should be responsible for... reflect it out
		ourMethod = AccurevLauncher.class.getDeclaredMethod("maskPasswordFromLogOutput", String.class);
		ourMethod.setAccessible(true);
	}
	
	@After
	public void teardown(){
		launcher = null;
		ourMethod = null;
	}
	
	@Test
	public void loginFails_NeedsToMasksPassword_perm1() throws Exception{
		String permutation1 = "accurev.exe login -H somehost:1234 -n thisisme thisIsMyPassword";
		assertEquals("accurev.exe login -H somehost:1234 -n thisisme ***********", ourMethod.invoke(launcher, permutation1));
	}
	
	@Test
	public void loginFails_NeedsToMasksPassword_perm2() throws Exception{
		String permutation2 = "accurev.exe login -H somehost:1234 thisisme thisIsMyPassword";
		assertEquals("accurev.exe login -H somehost:1234 thisisme ***********", ourMethod.invoke(launcher, permutation2));
	}
	
	@Test
	public void loginFails_NeedsToMasksPassword_perm3() throws Exception{
		String permutation3 = "accurev.exe login -H somehost:1234 -n thisisme th!sIsMyP4ssword!#@!&$1237t7";
		assertEquals("accurev.exe login -H somehost:1234 -n thisisme ***********", ourMethod.invoke(launcher, permutation3));
	}
	
	@Test
	public void loginFails_NeedsToMasksPassword_perm4() throws Exception{
		String permutation4 = "\"C:\\program files\\derp\\accurev.exe\" login -H somehost:1234 -n thisisme thisIsMyPassword";
		assertEquals("\"C:\\program files\\derp\\accurev.exe\" login -H somehost:1234 -n thisisme ***********", ourMethod.invoke(launcher, permutation4));
	}
	
	@Test
	public void loginFails_NeedsToMasksPassword_perm5() throws Exception{
		String permutation5 = "accurev.exe login -n thisisme thisIsMyPassword";
		assertEquals("accurev.exe login -n thisisme ***********", ourMethod.invoke(launcher, permutation5));
	}
	
	@Test
	public void loginFails_NeedsToMasksPassword_perm6() throws Exception{
		String permutation6 = "accurev.exe login -H somehostwithlogininitsnameforsomereason:1234 -n thisisme thisIsMyPassword";
		assertEquals("accurev.exe login -H somehostwithlogininitsnameforsomereason:1234 -n thisisme ***********", ourMethod.invoke(launcher, permutation6));
	}
	
	@Test
	public void loginFails_NeedsToMasksPassword_perm7() throws Exception{
		String permutation7 = "accurev.exe update -H somehostwithlogininitsnameforsomereason:1234 someworkspace thisisntrelatedtologins";
		assertEquals("accurev.exe update -H somehostwithlogininitsnameforsomereason:1234 someworkspace thisisntrelatedtologins", ourMethod.invoke(launcher, permutation7));
	}
}
