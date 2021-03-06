package org.jenkinsci.plugins.autocompleteparameter.providers;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.NotImplementedException;
import org.jenkinsci.plugins.autocompleteparameter.GlobalVariableUtils;
import org.jenkinsci.plugins.autocompleteparameter.JSONUtils;
import org.jenkinsci.plugins.autocompleteparameter.SafeJenkins;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.jenkinsci.plugins.scriptsecurity.scripts.ClasspathEntry;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.Binding;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import wirelabs.commons.RequestBuilder;

public class GroovyDataProvider extends AutocompleteDataProvider {
	private static final long serialVersionUID = -6438474876305562245L;
	private String script;
	private boolean sandbox;
	
	@SuppressFBWarnings(value="SE_BAD_FIELD", justification="ClasspathEntry has a single field URL which is actually Serializable")
	private LinkedList<ClasspathEntry> classpath;
	
	@DataBoundConstructor
	public GroovyDataProvider(String script, boolean sandbox, LinkedList<ClasspathEntry> classpath) {
		this.script = script;
		this.sandbox = sandbox;
		this.classpath = classpath;
	}
	
	@Override
	public Collection<?> getData() {
       return runScript(script, sandbox, classpath);
	}

	@Override
	public Collection<?> filter(String query) {
		throw new NotImplementedException("Filter not implemented for " + getClass().getSimpleName());
	}

	public String getScript() {
		return script;
	}

	public void setScript(String script) {
		this.script = script;
	}
	
	public LinkedList<ClasspathEntry> getClasspath() {
		return classpath;
	}
	
	public void setClasspath(LinkedList<ClasspathEntry> classpath) {
		this.classpath = classpath;
	}

	public boolean getSandbox() {
		return sandbox;
	}
	
	public void setSandbox(boolean sandbox) {
		this.sandbox = sandbox;
	} 

	@Extension
	public static final class DescriptorImpl extends Descriptor<AutocompleteDataProvider> {
		@Override
		public String getDisplayName() {
			return "Groovy script";
		}
		
        public FormValidation doTest(StaplerRequest req, @QueryParameter final String script, @QueryParameter final boolean sandbox, final LinkedList<ClasspathEntry> classpath)
        {
            try
            {
            	return FormValidation.ok(JSONUtils.toJSON(
            			executeWithTimeout(new Callable<Collection<?>>() {
							@Override
							public Collection<?> call() throws Exception {
								return runScript(script, sandbox, classpath);
							}
						}, 2, TimeUnit.MINUTES)));
            }
            catch(Exception e)
            {
                return FormValidation.error(e, "Failed to execute script");
            }
        }

	}
	
	private static Collection<?> runScript(String script, boolean sandbox, List<ClasspathEntry> classpath) {
		if (classpath == null)
			classpath = Collections.<ClasspathEntry>emptyList();
		
		SecureGroovyScript groovyScript = new SecureGroovyScript(script, sandbox, classpath).configuringWithKeyItem();
		
		ClassLoader cl = SafeJenkins.getInstanceOrCry().getPluginManager().uberClassLoader;

        if (cl == null) 
            cl = Thread.currentThread().getContextClassLoader();

        Binding binding = new Binding();
        for (Entry<String, String> envVar : GlobalVariableUtils.getGlobalVariables().entrySet()) 
			binding.setVariable(envVar.getKey(), envVar.getValue());
        
        binding.setVariable("requestBuilder", new RequestBuilder());
        
        Object out;
		try {
			out = groovyScript.evaluate(cl, binding);
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
		}
		if(out == null)
			return Collections.emptyList();
		
		if (out instanceof Collection)
			return (Collection<?>) out;

		if (out instanceof String)
			return JSONUtils.toCanonicalCollection(out.toString());
        
        throw new IllegalStateException("");
	}	
}
