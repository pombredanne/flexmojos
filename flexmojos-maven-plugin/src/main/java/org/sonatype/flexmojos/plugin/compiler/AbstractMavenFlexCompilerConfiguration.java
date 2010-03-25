package org.sonatype.flexmojos.plugin.compiler;

import static ch.lambdaj.Lambda.filter;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.not;
import static org.sonatype.flexmojos.matcher.artifact.ArtifactMatcher.artifactId;
import static org.sonatype.flexmojos.matcher.artifact.ArtifactMatcher.classifier;
import static org.sonatype.flexmojos.matcher.artifact.ArtifactMatcher.groupId;
import static org.sonatype.flexmojos.matcher.artifact.ArtifactMatcher.scope;
import static org.sonatype.flexmojos.matcher.artifact.ArtifactMatcher.type;
import static org.sonatype.flexmojos.plugin.common.FlexClassifier.LINK_REPORT;
import static org.sonatype.flexmojos.plugin.common.FlexExtension.CSS;
import static org.sonatype.flexmojos.plugin.common.FlexExtension.RB_SWC;
import static org.sonatype.flexmojos.plugin.common.FlexExtension.SWC;
import static org.sonatype.flexmojos.plugin.common.FlexExtension.XML;
import static org.sonatype.flexmojos.plugin.common.FlexScopes.COMPILE;
import static org.sonatype.flexmojos.plugin.common.FlexScopes.EXTERNAL;
import static org.sonatype.flexmojos.plugin.common.FlexScopes.INTERNAL;
import static org.sonatype.flexmojos.plugin.common.FlexScopes.MERGED;
import static org.sonatype.flexmojos.plugin.common.FlexScopes.THEME;
import static org.sonatype.flexmojos.plugin.utilities.CollectionUtils.merge;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.lang.ArrayUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Developer;
import org.apache.maven.model.FileSet;
import org.apache.maven.model.PatternSet;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.hamcrest.Matcher;
import org.sonatype.flexmojos.compiler.ICompilerConfiguration;
import org.sonatype.flexmojos.compiler.IDefaultScriptLimits;
import org.sonatype.flexmojos.compiler.IDefaultSize;
import org.sonatype.flexmojos.compiler.IDefine;
import org.sonatype.flexmojos.compiler.IExtension;
import org.sonatype.flexmojos.compiler.IExtensionsConfiguration;
import org.sonatype.flexmojos.compiler.IFontsConfiguration;
import org.sonatype.flexmojos.compiler.IFrame;
import org.sonatype.flexmojos.compiler.IFramesConfiguration;
import org.sonatype.flexmojos.compiler.ILanguageRange;
import org.sonatype.flexmojos.compiler.ILanguages;
import org.sonatype.flexmojos.compiler.ILicense;
import org.sonatype.flexmojos.compiler.ILicensesConfiguration;
import org.sonatype.flexmojos.compiler.ILocalizedDescription;
import org.sonatype.flexmojos.compiler.ILocalizedTitle;
import org.sonatype.flexmojos.compiler.IMetadataConfiguration;
import org.sonatype.flexmojos.compiler.IMxmlConfiguration;
import org.sonatype.flexmojos.compiler.INamespace;
import org.sonatype.flexmojos.compiler.INamespacesConfiguration;
import org.sonatype.flexmojos.compiler.IRuntimeSharedLibraryPath;
import org.sonatype.flexmojos.compiler.command.Result;
import org.sonatype.flexmojos.plugin.compiler.attributes.MavenArtifact;
import org.sonatype.flexmojos.plugin.compiler.attributes.MavenDefaultScriptLimits;
import org.sonatype.flexmojos.plugin.compiler.attributes.MavenDefaultSize;
import org.sonatype.flexmojos.plugin.compiler.attributes.MavenExtension;
import org.sonatype.flexmojos.plugin.compiler.attributes.MavenFrame;
import org.sonatype.flexmojos.plugin.compiler.attributes.MavenMetadataConfiguration;
import org.sonatype.flexmojos.plugin.compiler.attributes.MavenNamespace;
import org.sonatype.flexmojos.plugin.compiler.attributes.MavenRuntimeException;
import org.sonatype.flexmojos.plugin.compiler.flexbridge.MavenLogger;
import org.sonatype.flexmojos.plugin.compiler.flexbridge.MavenPathResolver;
import org.sonatype.flexmojos.plugin.compiler.lazyload.Cacheable;
import org.sonatype.flexmojos.plugin.utilities.CollectionUtils;
import org.sonatype.flexmojos.plugin.utilities.ConfigurationResolver;
import org.sonatype.flexmojos.plugin.utilities.MavenUtils;
import org.sonatype.flexmojos.test.util.PathUtil;

import flex2.compiler.Logger;
import flex2.compiler.common.SinglePathResolver;
import flex2.tools.oem.internal.OEMLogAdapter;

public abstract class AbstractMavenFlexCompilerConfiguration<CFG, C extends AbstractMavenFlexCompilerConfiguration<CFG, C>>
    implements ICompilerConfiguration, IFramesConfiguration, ILicensesConfiguration, IMetadataConfiguration,
    IFontsConfiguration, ILanguages, IMxmlConfiguration, INamespacesConfiguration, IExtensionsConfiguration, Cacheable,
    Cloneable
{

    protected static final DateFormat DATE_FORMAT = new SimpleDateFormat();

    protected static final String FRAMEWORK_GROUP_ID = "com.adobe.flex.framework";

    protected static final Matcher<? extends Artifact> GLOBAL_MATCHER =
        allOf( groupId( FRAMEWORK_GROUP_ID ), type( SWC ), anyOf( artifactId( "playerglobal" ),
                                                                  artifactId( "airglobal" ) ) );

    /**
     * Generate an accessible SWF
     * <p>
     * Equivalent to -compiler.accessible
     * </p>
     * 
     * @parameter expression="${flex.accessible}"
     */
    private Boolean accessible;

    /**
     * Specifies actionscript file encoding. If there is no BOM in the AS3 source files, the compiler will use this file
     * encoding.
     * <p>
     * Equivalent to -compiler.actionscript-file-encoding
     * </p>
     * 
     * @parameter expression="${flex.actionscriptFileEncoding}"
     */
    private String actionscriptFileEncoding;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -compiler.adjust-opdebugline
     * </p>
     * 
     * @parameter expression="${flex.adjustOpdebugline}"
     */
    private Boolean adjustOpdebugline;

    /**
     * Enables advanced anti-aliasing for embedded fonts, which provides greater clarity for small fonts
     * <p>
     * Equivalent to -compiler.fonts.advanced-anti-aliasing
     * </p>
     * 
     * @parameter expression="${flex.advancedAntiAliasing}"
     */
    private Boolean advancedAntiAliasing;

    /**
     * If true, a style manager will add style declarations to the local style manager without checking to see if the
     * parent already has the same style selector with the same properties. If false, a style manager will check the
     * parent to make sure a style with the same properties does not already exist before adding one locally.<BR>
     * If there is no local style manager created for this application, then don't check for duplicates. Just use the
     * old "selector exists" test.
     * <p>
     * Equivalent to -compiler.allow-duplicate-style-declaration
     * </p>
     * 
     * @parameter expression="${flex.allowDuplicateDefaultStyleDeclarations}"
     */
    private Boolean allowDuplicateDefaultStyleDeclarations;

    /**
     * checks if a source-path entry is a subdirectory of another source-path entry. It helps make the package names of
     * MXML components unambiguous.
     * <p>
     * Equivalent to -compiler.allow-source-path-overlap
     * </p>
     * 
     * @parameter expression="${flex.allowSourcePathOverlap}"
     */
    private Boolean allowSourcePathOverlap;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -compiler.archive-classes-and-assets
     * </p>
     * 
     * @parameter expression="${flex.archiveClassesAndAssets}"
     */
    private Boolean archiveClassesAndAssets;

    /**
     * @component
     * @readonly
     */
    protected ArchiverManager archiverManager;

    /**
     * Use the ActionScript 3 class based object model for greater performance and better error reporting. In the class
     * based object model most built-in functions are implemented as fixed methods of classes
     * <p>
     * Equivalent to -compiler.as3
     * </p>
     * 
     * @parameter expression="${flex.as3}"
     */
    private Boolean as3;

    /**
     * Output performance benchmark
     * <p>
     * Equivalent to -benchmark
     * </p>
     * 
     * @parameter expression="${flex.benchmark}"
     */
    protected Boolean benchmark;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -benchmark-compiler-details
     * </p>
     * 0 = none, 1 = light, 5 = verbose
     * 
     * @parameter expression="${flex.benchmarkCompilerDetails}"
     */
    private Integer benchmarkCompilerDetails;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -benchmark-time-filter
     * </p>
     * min time of units to log in ms
     * 
     * @parameter expression="${flex.benchmarkTimeFilter}"
     */
    private Long benchmarkTimeFilter;

    private Map<String, Object> cache = new LinkedHashMap<String, Object>();

    /**
     * Classifier to add to the artifact generated. If given, the artifact will be an attachment instead.
     * 
     * @parameter expression="${flex.classifier}"
     */
    protected String classifier;

    /**
     * Specifies a compatibility version
     * <p>
     * Equivalent to -compiler.mxml.compatibility-version
     * </p>
     * 
     * @parameter expression="${flex.compatibilityVersion}"
     */
    private String compatibilityVersion;

    /**
     * @component
     * @readonly
     */
    protected org.sonatype.flexmojos.compiler.FlexCompiler compiler;

    /**
     * Specifies the locale for internationalization
     * <p>
     * Equivalent to -compiler.locale
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;compilerLocales&gt;
     *   &lt;locale&gt;en_US&lt;/locale&gt;
     * &lt;/compilerLocales&gt;
     * </pre>
     * 
     * @parameter
     */
    protected String[] compilerLocales;

    /**
     * A list of warnings that should be enabled/disabled
     * <p>
     * Equivalent to -compiler.show-actionscript-warnings, -compiler.show-binding-warnings,
     * -compiler.show-shadowed-device-font-warnings, -compiler.show-unused-type-selector-warnings and -compiler.warn-*
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;compilerWarnings&gt;
     *   &lt;show-actionscript-warnings&gt;true&lt;/show-actionscript-warnings&gt;
     *   &lt;warn-bad-nan-comparison&gt;false&lt;/warn-bad-nan-comparison&gt;
     * &lt;/compilerWarnings&gt;
     * </pre>
     * 
     * @parameter
     */
    private Map<String, Boolean> compilerWarnings = new LinkedHashMap<String, Boolean>();

    /**
     * The maven configuration directory
     * 
     * @parameter expression="${basedir}/src/main/config"
     * @required
     * @readonly
     */
    protected File configDirectory;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -compiler.conservative
     * </p>
     * compiler algorithm settings
     * 
     * @parameter expression="${flex.conservative}"
     */
    private Boolean conservative;

    /**
     * Path to replace {context.root} tokens for service channel endpoints
     * <p>
     * Equivalent to -compiler.context-root
     * </p>
     * 
     * @parameter expression="${flex.contextRoot}"
     */
    private String contextRoot;

    /**
     * Generates a movie that is suitable for debugging
     * <p>
     * Equivalent to -compiler.debug
     * </p>
     * 
     * @parameter expression="${flex.debug}"
     */
    private Boolean debug;

    /**
     * The password to include in debuggable SWFs
     * <p>
     * Equivalent to -debug-password
     * </p>
     * 
     * @parameter expression="${flex.debugPassword}"
     */
    protected String debugPassword;

    /**
     * Default background color (may be overridden by the application code)
     * <p>
     * Equivalent to -default-background-color
     * </p>
     * 
     * @parameter expression="${flex.defaultBackgroundColor}"
     */
    private Integer defaultBackgroundColor;

    /**
     * Default frame rate to be used in the SWF
     * <p>
     * Equivalent to -default-frame-rate
     * </p>
     * 
     * @parameter expression="${flex.defaultFrameRate}"
     */
    private Integer defaultFrameRate;

    /**
     * Default value of resourceBundleList used when it is not defined
     * 
     * @parameter default-value="${project.build.directory}/${project.build.finalName}-rb.properties"
     * @readonly
     */
    private File defaultResourceBundleList;

    /**
     * Default script execution limits (may be overridden by root attributes)
     * <p>
     * Equivalent to -default-script-limits
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;defaultScriptLimits&gt;
     *   &lt;maxExecutionTime&gt;???&lt;/maxExecutionTime&gt;
     *   &lt;maxRecursionDepth&gt;???&lt;/maxRecursionDepth&gt;
     * &lt;/defaultScriptLimits&gt;
     * </pre>
     * 
     * @parameter
     */
    private MavenDefaultScriptLimits defaultScriptLimits;

    /**
     * Location of defaults style stylesheets
     * <p>
     * Equivalent to -compiler.defaults-css-url
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;defaultsCssFiles&gt;
     *   &lt;defaultsCssFile&gt;???&lt;/defaultsCssFile&gt;
     *   &lt;defaultsCssFile&gt;???&lt;/defaultsCssFile&gt;
     * &lt;/defaultsCssFiles&gt;
     * </pre>
     * 
     * @parameter
     */
    private File[] defaultsCssFiles;

    /**
     * Defines the location of the default style sheet. Setting this option overrides the implicit use of the
     * defaults.css style sheet in the framework.swc file
     * <p>
     * Equivalent to -compiler.defaults-css-url
     * </p>
     * 
     * @parameter expression="${flex.defaultsCssUrl}"
     */
    private String defaultsCssUrl;

    /**
     * Default application size (may be overridden by root attributes in the application)
     * <p>
     * Equivalent to -default-size
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;defaultSize&gt;
     *   &lt;height&gt;???&lt;/height&gt;
     *   &lt;width&gt;???&lt;/width&gt;
     * &lt;/defaultSize&gt;
     * </pre>
     * 
     * @parameter
     */
    private MavenDefaultSize defaultSize;

    /**
     * Define a global AS3 conditional compilation definition, e.g. -define=CONFIG::debugging,true or
     * -define+=CONFIG::debugging,true (to append to existing definitions in flex-config.xml)
     * <p>
     * Equivalent to -compiler.define
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;defines&gt;
     *   &lt;property&gt;
     *     &lt;name&gt;SOMETHING::aNumber&lt;/name&gt;
     *     &lt;value&gt;2.2&lt;/value&gt;
     *   &lt;/property&gt;
     *   &lt;property&gt;
     *     &lt;name&gt;SOMETHING::aString&lt;/name&gt;
     *     &lt;value&gt;&quot;text&quot;&lt;/value&gt;
     *   &lt;/property&gt;
     * &lt;/defines&gt;
     * </pre>
     * 
     * @parameter
     */
    private Properties defines;

    /**
     * Back-door to disable optimizations in case they are causing problems
     * <p>
     * Equivalent to -compiler.disable-incremental-optimizations
     * </p>
     * 
     * @parameter expression="${flex.disableIncrementalOptimizations}"
     */
    private Boolean disableIncrementalOptimizations;

    /**
     * DOCME undocumented
     * <p>
     * Equivalent to -compiler.doc
     * </p>
     * 
     * @parameter expression="${flex.doc}"
     */
    private Boolean doc;

    /**
     * Write a file containing all currently set configuration values in a format suitable for use as a flex config file
     * <p>
     * Equivalent to -dump-config
     * </p>
     * 
     * @parameter expression="${flex.dumpConfig}"
     */
    private File dumpConfig;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -compiler.enable-runtime-design-layers
     * </p>
     * 
     * @parameter expression="${flex.enableRuntimeDesignLayers}"
     */
    private Boolean enableRuntimeDesignLayers;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -compiler.enable-swc-version-filtering
     * </p>
     * 
     * @parameter expression="${flex.enableSwcVersionFiltering}"
     */
    private Boolean enableSwcVersionFiltering;

    /**
     * Use the ECMAScript edition 3 prototype based object model to allow dynamic overriding of prototype properties. In
     * the prototype based object model built-in functions are implemented as dynamic properties of prototype objects
     * <p>
     * Equivalent to -compiler.es
     * </p>
     * 
     * @parameter expression="${flex.es}"
     */
    private Boolean es;

    /**
     * Configure extensions to flex compiler
     * <p>
     * Equivalent to -compiler.extensions.extension
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;extensions&gt;
     *   &lt;extension&gt;
     *     &lt;extensionArtifact&gt;
     *       &lt;groupId&gt;org.myproject&lt;/groupId&gt;
     *       &lt;artifactId&gt;my-extension&lt;/artifactId&gt;
     *       &lt;version&gt;1.0&lt;/version&gt;
     *     &lt;/extensionArtifact&gt;
     *     &lt;parameters&gt;
     *       &lt;parameter&gt;param1&lt;/parameter&gt;
     *       &lt;parameter&gt;param2&lt;/parameter&gt;
     *       &lt;parameter&gt;param3&lt;/parameter&gt;
     *     &lt;/parameters&gt;
     *   &lt;/extension&gt;
     * &lt;/extensions&gt;
     * </pre>
     * 
     * @parameter
     */
    private MavenExtension[] extensions;

    /**
     * A list of symbols to omit from linking when building a SWF
     * <p>
     * Equivalent to -externs
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;externs&gt;
     *   &lt;extern&gt;???&lt;/extern&gt;
     *   &lt;extern&gt;???&lt;/extern&gt;
     * &lt;/externs&gt;
     * </pre>
     * 
     * @parameter
     */
    private String[] externs;

    /**
     * The name of the compiled file
     * 
     * @parameter default-name="${project.build.finalName}" expression="${flex.finalName}"
     */
    protected String finalName;

    /**
     * Enables FlashType for embedded fonts, which provides greater clarity for small fonts
     * <p>
     * Equivalent to -compiler.fonts.flash-type
     * </p>
     * 
     * @parameter expression="${flex.flashType}"
     */
    private Boolean flashType;

    /**
     * A SWF frame label with a sequence of classnames that will be linked onto the frame
     * <p>
     * Equivalent to -frames.frame
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;frames&gt;
     *   &lt;frame&gt;
     *     &lt;label&gt;???&lt;/label&gt;
     *     &lt;classNames&gt;
     *       &lt;className&gt;???&lt;/className&gt;
     *       &lt;className&gt;???&lt;/className&gt;
     *     &lt;/classNames&gt;
     *   &lt;/frame&gt;
     * &lt;/frames&gt;
     * </pre>
     * 
     * @parameter
     */
    private MavenFrame[] frames;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -framework
     * </p>
     * 
     * @parameter expression="${flex.framework}"
     */
    private String framework;

    /**
     * When false (faster) Flexmojos will compiler modules and resource bundles using multiple threads (One per SWF). If
     * true, Thread.join() will be invoked to make the execution synchronous (sequential).
     * 
     * @parameter expression="${flex.fullSynchronization}" default-value="false"
     */
    protected boolean fullSynchronization;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -compiler.generate-abstract-syntax-tree
     * </p>
     * 
     * @parameter expression="${flex.generateAbstractSyntaxTree}"
     */
    private Boolean generateAbstractSyntaxTree;

    /**
     * DOCME Undocumented by adobe
     * <p>
     * Equivalent to -generated-frame-loader
     * </p>
     * 
     * @parameter expression="${flex.generateFrameLoader}"
     */
    private Boolean generateFrameLoader;

    /**
     * A flag to set when Flex is running on a server without a display
     * <p>
     * Equivalent to -compiler.headless-server
     * </p>
     * 
     * @parameter expression="${flex.headlessServer}"
     */
    private Boolean headlessServer;

    /**
     * A list of resource bundles to include in the output SWC
     * <p>
     * Equivalent to -include-resource-bundles
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;includeResourceBundles&gt;
     *   &lt;rb&gt;SharedResources&lt;/rb&gt;
     *   &lt;rb&gt;Collections&lt;/rb&gt;
     * &lt;/includeResourceBundles&gt;
     * </pre>
     * 
     * @parameter
     */
    protected List<String> includeResourceBundles;

    /**
     * A list of symbols to always link in when building a SWF
     * <p>
     * Equivalent to -includes
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;includes&gt;
     *   &lt;include&gt;???&lt;/include&gt;
     *   &lt;include&gt;???&lt;/include&gt;
     * &lt;/includes&gt;
     * </pre>
     * 
     * @parameter
     */
    private String[] includes;

    /**
     * Enables incremental compilation
     * <p>
     * Equivalent to -compiler.incremental
     * </p>
     * 
     * @parameter expression="${flex.incremental}"
     */
    private Boolean incremental;

    /**
     * Enables the compiled application or module to set styles that only affect itself and its children.<BR>
     * Allow the user to decide if the compiled application/module should have its own style manager
     * <p>
     * Equivalent to -compiler.isolate-styles
     * </p>
     * 
     * @parameter expression="${flex.isolateStyles}"
     */
    private Boolean isolateStyles;

    /**
     * Disables the pruning of unused CSS type selectors
     * <p>
     * Equivalent to -compiler.keep-all-type-selectors
     * </p>
     * 
     * @parameter expression="${flex.keepAllTypeSelectors}"
     */
    private Boolean keepAllTypeSelectors;

    /**
     * Keep the specified metadata in the SWF
     * <p>
     * Equivalent to -compiler.keep-as3-metadata
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;keepAs3Metadatas&gt;
     *   &lt;keepAs3Metadata&gt;Bindable&lt;/keepAs3Metadata&gt;
     *   &lt;keepAs3Metadata&gt;Events&lt;/keepAs3Metadata&gt;
     * &lt;/keepAs3Metadatas&gt;
     * </pre>
     * 
     * @parameter
     */
    private String[] keepAs3Metadatas;

    /**
     * Keep the specified metadata in the SWF
     * <p>
     * Equivalent to -compiler.keep-generated-actionscript
     * </p>
     * 
     * @parameter expression="${flex.keepGeneratedActionscript}"
     */
    private Boolean keepGeneratedActionscript;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -compiler.keep-generated-signatures
     * </p>
     * 
     * @parameter expression="${flex.keepGeneratedSignatures}"
     */
    private Boolean keepGeneratedSignatures;

    /**
     * A range to restrict the number of font glyphs embedded into the SWF
     * <p>
     * Equivalent to -compiler.fonts.languages.language-range
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;languageRange&gt;
     *   &lt;lang&gt;range&lt;/lang&gt;
     * &lt;/languageRange&gt;
     * </pre>
     * 
     * @parameter
     */
    private Map<String, String> languageRange;

    /**
     * DOCME Undocumented by adobe
     * <p>
     * Equivalent to -lazy-init
     * </p>
     * 
     * @parameter expression="${flex.lazyInit}"
     */
    private Boolean lazyInit;

    /**
     * Specifies a product and a serial number
     * <p>
     * Equivalent to -licenses.license
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;licenses&gt;
     *   &lt;flexbuilder3&gt;xxxx-xxxx-xxxx-xxxx&lt;/flexbuilder3&gt;
     * &lt;/licenses&gt;
     * </pre>
     * 
     * @parameter
     */
    private Map<String, String> licenses;

    /**
     * When true the link report will be attached to maven reactor
     * 
     * @parameter expression="${flex.linkReportAttach}"
     */
    private boolean linkReportAttach;

    /**
     * Load a file containing configuration options.
     * <p>
     * Equivalent to -load-config
     * </p>
     * Overwrite loadConfigs when defined!
     * 
     * @parameter expression="${flex.loadConfig}"
     */
    private File loadConfig;

    /**
     * Load a file containing configuration options
     * <p>
     * Equivalent to -load-config
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;loadConfigs&gt;
     *   &lt;loadConfig&gt;???&lt;/loadConfig&gt;
     *   &lt;loadConfig&gt;???&lt;/loadConfig&gt;
     * &lt;/loadConfigs&gt;
     * </pre>
     * 
     * @parameter
     */
    private File[] loadConfigs;

    /**
     * An XML file containing &lt;def&gt;, &lt;pre&gt;, and &lt;ext&gt; symbols to omit from linking when building a SWF
     * <p>
     * Equivalent to -load-externs
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;loadExterns&gt;
     *   &lt;loadExtern&gt;???&lt;/loadExtern&gt;
     *   &lt;loadExtern&gt;???&lt;/loadExtern&gt;
     * &lt;/loadExterns&gt;
     * </pre>
     * 
     * @parameter
     */
    protected File[] loadExterns;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -compiler.fonts.local-font-paths
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;localFontPaths&gt;
     *   &lt;localFontPath&gt;???&lt;/localFontPath&gt;
     *   &lt;localFontPath&gt;???&lt;/localFontPath&gt;
     * &lt;/localFontPaths&gt;
     * </pre>
     * 
     * @parameter
     */
    private File[] localFontPaths;

    /**
     * Compiler font manager classes, in policy resolution order
     * <p>
     * Equivalent to -compiler.fonts.local-fonts-snapshot
     * </p>
     * 
     * @parameter expression="${flex.localFontsSnapshot}"
     */
    private File localFontsSnapshot;

    /**
     * Local repository to be used by the plugin to resolve dependencies.
     * 
     * @parameter expression="${localRepository}"
     */
    protected ArtifactRepository localRepository;

    /**
     * Maven logger
     * 
     * @readonly
     */
    private Log log;

    /**
     * Compiler font manager classes, in policy resolution order
     * <p>
     * Equivalent to -compiler.fonts.managers
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;managers&gt;
     *   &lt;manager&gt;???&lt;/manager&gt;
     *   &lt;manager&gt;???&lt;/manager&gt;
     * &lt;/managers&gt;
     * </pre>
     * 
     * @parameter
     */
    private List<String> managers;

    /**
     * Sets the maximum number of fonts to keep in the server cache
     * <p>
     * Equivalent to -compiler.fonts.max-cached-fonts
     * </p>
     * 
     * @parameter expression="${flex.maxCachedFonts}"
     */
    private Integer maxCachedFonts;

    /**
     * Sets the maximum number of character glyph-outlines to keep in the server cache for each font face
     * <p>
     * Equivalent to -compiler.fonts.max-glyphs-per-face
     * </p>
     * 
     * @parameter expression="${flex.maxGlyphsPerFace}"
     */
    private Integer maxGlyphsPerFace;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -compiler.memory-usage-factor
     * </p>
     * 
     * @parameter expression="${flex.memoryUsageFactor}"
     */
    private Integer memoryUsageFactor;

    /**
     * Information to store in the SWF metadata
     * <p>
     * Equivalent to: -metadata.contributor, -metadata.creator, -metadata.date, -metadata.description,
     * -metadata.language, -metadata.localized-description, -metadata.localized-title, -metadata.publisher,
     * -metadata.title
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;metadata&gt;
     *   &lt;contributors&gt;
     *     &lt;contributor&gt;???&lt;/contributor&gt;
     *   &lt;/contributors&gt;
     *   &lt;creators&gt;
     *     &lt;creator&gt;???&lt;/creator&gt;
     *   &lt;/creators&gt;
     *   &lt;date&gt;???&lt;/date&gt;
     *   &lt;description&gt;???&lt;/description&gt;
     *   &lt;languages&gt;
     *     &lt;language&gt;???&lt;/language&gt;
     *   &lt;/languages&gt;
     *   &lt;localizedDescriptions&gt;
     *     &lt;lang&gt;text&lt;/land&gt;
     *   &lt;/localizedDescriptions&gt;
     *   &lt;localizedTitles&gt;
     *     &lt;lang&gt;title&lt;/land&gt;
     *   &lt;/localizedTitles&gt;
     *   &lt;publishers&gt;
     *     &lt;publisher&gt;???&lt;/publisher&gt;
     *   &lt;/publishers&gt;
     *   &lt;title&gt;???&lt;/title&gt;
     * &lt;/metadata&gt;
     * </pre>
     * 
     * @parameter
     */
    private MavenMetadataConfiguration metadata;

    /**
     * Minimum supported SDK version for this library. This string will always be of the form N.N.N. For example, if
     * -minimum-supported-version=2, this string is "2.0.0", not "2".
     * <p>
     * Equivalent to -compiler.mxml.minimum-supported-version
     * </p>
     * 
     * @parameter expression="${flex.minimumSupportedVersion}"
     */
    private String minimumSupportedVersion;

    /**
     * Specify a URI to associate with a manifest of components for use as MXML elements
     * <p>
     * Equivalent to -compiler.namespaces.namespace
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;namespaces&gt;
     *   &lt;namespace&gt;
     *     &lt;uri&gt;http://www.adobe.com/2006/mxml&lt;/uri&gt;
     *     &lt;manifest&gt;${basedir}/manifest.xml&lt;/manifest&gt;
     *   &lt;/namespace&gt;
     * &lt;/namespaces&gt;
     * </pre>
     * 
     * @parameter
     */
    private MavenNamespace[] namespaces;

    /**
     * Toggle whether trace statements are omitted
     * <p>
     * Equivalent to -compiler.omit-trace-statements
     * </p>
     * 
     * @parameter expression="${flex.omitTraceStatements}"
     */
    private Boolean omitTraceStatements;

    /**
     * Enable post-link SWF optimization
     * <p>
     * Equivalent to -compiler.optimize
     * </p>
     * 
     * @parameter expression="${flex.optimize}"
     */
    private Boolean optimize;

    /**
     * The filename of the SWF movie to create
     * <p>
     * Equivalent to -output
     * </p>
     * 
     * @parameter expression="${flex.output}"
     * @required
     * @readonly
     */
    private File output;

    /**
     * @parameter expression="${project.build.outputDirectory}"
     * @readonly
     * @required
     */
    protected File outputDirectory;

    /**
     * @parameter expression="${project.packaging}"
     * @required
     * @readonly
     */
    protected String packaging;

    /**
     * @parameter expression="${plugin.artifacts}"
     * @readonly
     */
    protected List<Artifact> pluginArtifacts;

    /**
     * The maven project.
     * 
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * @component
     * @readonly
     * @required
     */
    protected MavenProjectHelper projectHelper;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -compiler.mxml.qualified-type-selectors
     * </p>
     * 
     * @parameter expression="${flex.qualifiedTypeSelectors}"
     */
    private Boolean qualifiedTypeSelectors;

    /**
     * XML text to store in the SWF metadata (overrides metadata.* configuration)
     * <p>
     * Equivalent to -raw-metadata
     * </p>
     * 
     * @parameter expression="${flex.rawMetadata}"
     */
    private String rawMetadata;

    /**
     * List of remote repositories to be used by the plugin to resolve dependencies.
     * 
     * @parameter expression="${project.remoteArtifactRepositories}"
     */
    protected List<ArtifactRepository> remoteRepositories;

    /**
     * @component
     * @readonly
     */
    protected RepositorySystem repositorySystem;

    /**
     * Prints a list of resource bundles to a file for input to the compc compiler to create a resource bundle SWC file.
     * <p>
     * Equivalent to -resource-bundle-list
     * </p>
     * 
     * @parameter expression="${flex.resourceBundleList}"
     */
    private File resourceBundleList;

    /**
     * This undocumented option is for compiler performance testing. It allows the Flex 3 compiler to compile the Flex 2
     * framework and Flex 2 apps. This is not an officially-supported combination
     * <p>
     * Equivalent to -compiler.resource-hack
     * </p>
     * 
     * @parameter expression="${flex.resourceHack}"
     */
    private Boolean resourceHack;

    /**
     * The maven resources
     * 
     * @parameter expression="${project.build.resources}"
     * @required
     * @readonly
     */
    protected List<Resource> resources;

    /**
     * Specifies the locales for external internationalization bundles
     * <p>
     * No equivalent parameter
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;runtimeLocales&gt;
     *   &lt;locale&gt;en_US&lt;/locale&gt;
     * &lt;/runtimeLocales&gt;
     * </pre>
     * 
     * @parameter
     */
    protected String[] runtimeLocales;

    /**
     * A list of runtime shared library URLs to be loaded before the application starts
     * <p>
     * Equivalent to -runtime-shared-libraries
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;runtimeSharedLibraries&gt;
     *   &lt;runtimeSharedLibrary&gt;???&lt;/runtimeSharedLibrary&gt;
     *   &lt;runtimeSharedLibrary&gt;???&lt;/runtimeSharedLibrary&gt;
     * &lt;/runtimeSharedLibraries&gt;
     * </pre>
     * 
     * @parameter
     */
    private String[] runtimeSharedLibraries;

    /**
     * Path to Flex Data Services configuration file
     * <p>
     * Equivalent to -compiler.services
     * </p>
     * 
     * @parameter expression="${flex.services}"
     */
    private File services;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -compiler.signature-directory
     * </p>
     * 
     * @parameter expression="${flex.signatureDirectory}"
     */
    private File signatureDirectory;

    /**
     * The maven compile source roots
     * <p>
     * Equivalent to -compiler.source-path
     * </p>
     * List of path elements that form the roots of ActionScript class
     * 
     * @parameter expression="${project.compileSourceRoots}"
     * @required
     * @readonly
     */
    private List<String> compileSourceRoots;

    /**
     * Statically link the libraries specified by the -runtime-shared-libraries-path option.
     * <p>
     * Equivalent to -static-link-runtime-shared-libraries
     * </p>
     * 
     * @parameter expression="${flex.staticLinkRuntimeSharedLibraries}"
     */
    private Boolean staticLinkRuntimeSharedLibraries;

    /**
     * Runs the AS3 compiler in strict error checking mode
     * <p>
     * Equivalent to -compiler.strict
     * </p>
     * 
     * @parameter expression="${flex.strict}"
     */
    private Boolean strict;

    /**
     * If true optimization using signature checksums are enabled
     * <p>
     * Equivalent to -swc-checksum
     * </p>
     * 
     * @parameter expression="${flex.swcChecksum}"
     */
    private Boolean swcChecksum;

    /**
     * @parameter expression="${project.build.directory}"
     * @readonly
     * @required
     */
    protected File targetDirectory;

    /**
     * Specifies the version of the player the application is targeting. Features requiring a later version will not be
     * compiled into the application. The minimum value supported is "9.0.0".
     * <p>
     * Equivalent to -target-player
     * </p>
     * 
     * @parameter expression="${flex.targetPlayer}"
     */
    private String targetPlayer;

    /**
     * List of CSS or SWC files to apply as a theme
     * <p>
     * Equivalent to -compiler.theme
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;themes&gt;
     *    &lt;theme&gt;css/main.css&lt;/theme&gt;
     * &lt;/themes&gt;
     * </pre>
     * 
     * If you are using SWC theme should be better keep it's version controlled, so is advised to use a dependency with
     * theme scope.<BR>
     * Like this:
     * 
     * <pre>
     * &lt;dependency&gt;
     *   &lt;groupId&gt;com.acme&lt;/groupId&gt;
     *   &lt;artifactId&gt;acme-theme&lt;/artifactId&gt;
     *   &lt;type&gt;swc&lt;/type&gt;
     *   &lt;scope&gt;theme&lt;/scope&gt;
     *   &lt;version&gt;1.0&lt;/version&gt;
     * &lt;/dependency&gt;
     * </pre>
     * 
     * @parameter
     */
    private File[] themes;

    /**
     * Configures the LocalizationManager's locale, which is used when reporting compile time errors, warnings, and
     * info. For example, "en" or "ja_JP".
     * <p>
     * Equivalent to -tools-locale
     * </p>
     * 
     * @parameter expression="${flex.toolsLocale}" default-value="en_US"
     */
    private String toolsLocale;

    /**
     * DOCME undocumented by adobe
     * <p>
     * Equivalent to -compiler.translation-format
     * </p>
     * 
     * @parameter expression="${flex.translationFormat}"
     */
    private String translationFormat;

    /**
     * Determines whether resources bundles are included in the application
     * <p>
     * Equivalent to -compiler.use-resource-bundle-metadata
     * </p>
     * 
     * @parameter expression="${flex.useResourceBundleMetadata}"
     */
    private Boolean useResourceBundleMetadata;

    /**
     * Toggle whether the SWF is flagged for access to network resources
     * <p>
     * Equivalent to -use-network
     * </p>
     * 
     * @parameter expression="${flex.userNetwork}"
     */
    private Boolean userNetwork;

    /**
     * Save callstack information to the SWF for debugging
     * <p>
     * Equivalent to -compiler.verbose-stacktraces
     * </p>
     * 
     * @parameter expression="${flex.verboseStacktraces}"
     */
    private Boolean verboseStacktraces;

    /**
     * Verifies the libraries loaded at runtime are the correct ones
     * <p>
     * Equivalent to -verify-digests
     * </p>
     * 
     * @parameter expression="${flex.verifyDigests}"
     */
    private Boolean verifyDigests;

    /**
     * Toggle the display of warnings
     * <p>
     * Equivalent to -warnings
     * </p>
     * 
     * @parameter expression="${flex.warnings}"
     */
    private Boolean warnings;

    protected void checkResult( Result result )
        throws MojoFailureException, MojoExecutionException
    {
        int exitCode;
        try
        {
            exitCode = result.getExitCode();
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
        if ( exitCode != 0 )
        {
            throw new MojoFailureException( "Got " + exitCode + " errors building project, check logs" );
        }
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public C clone()
    {
        try
        {
            C clone = (C) super.clone();
            clone.cache = new LinkedHashMap<String, Object>();
            return clone;
        }
        catch ( CloneNotSupportedException e )
        {
            throw new IllegalStateException( "The class '" + getClass() + "' is supposed to be clonable", e );
        }
    }

    @SuppressWarnings( "unchecked" )
    protected void configureResourceBundle( String locale, AbstractMavenFlexCompilerConfiguration<?, ?> cfg )
    {
        cfg.compilerLocales = new String[] { locale };
        cfg.classifier = locale;
        cfg.includeResourceBundles = CollectionUtils.merge( getResourceBundleListContent(), includeResourceBundles );
        cfg.getCache().put( "getExternalLibraryPath",
                            merge( cfg.getIncludeLibraries(), cfg.getExternalLibraryPath() ).toArray( new File[0] ) );
        cfg.getCache().put( "getIncludeLibraries", new File[0] );
    }

    public abstract Result doCompile( CFG cfg, boolean synchronize )
        throws Exception;

    protected Result executeCompiler( CFG cfg, boolean synchronize )
        throws MojoExecutionException, MojoFailureException
    {
        Result result;
        try
        {
            result = doCompile( cfg, synchronize );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }

        if ( synchronize )
        {
            checkResult( result );
        }

        return result;
    }

    protected List<String> filterClasses( PatternSet[] classesPattern, File[] directories )
    {
        List<String> classes = new ArrayList<String>();

        for ( File directory : directories )
        {
            if ( !directory.exists() )
            {
                continue;
            }

            for ( PatternSet pattern : classesPattern )
            {
                if ( pattern instanceof FileSet )
                {
                    File dir = PathUtil.getCanonicalFile( ( (FileSet) pattern ).getDirectory() );
                    if ( !ArrayUtils.contains( directories, dir ) )
                    {
                        throw new IllegalArgumentException( "Pattern does point to an invalid source directory: "
                            + dir.getAbsolutePath() );
                    }
                }

                DirectoryScanner scanner = scan( directory, pattern );

                String[] included = scanner.getIncludedFiles();
                for ( String file : included )
                {
                    String classname = file;
                    classname = classname.replaceAll( "\\.(.)*", "" );
                    classname = classname.replace( '\\', '.' );
                    classname = classname.replace( '/', '.' );
                    classes.add( classname );
                }
            }
        }

        return classes;
    }

    public Boolean getAccessible()
    {
        return accessible;
    }

    public String getActionscriptFileEncoding()
    {
        return actionscriptFileEncoding;
    }

    public Boolean getAdjustOpdebugline()
    {
        return adjustOpdebugline;
    }

    public Boolean getAdvancedAntiAliasing()
    {
        return advancedAntiAliasing;
    }

    public Boolean getAllowDuplicateDefaultStyleDeclarations()
    {
        return allowDuplicateDefaultStyleDeclarations;
    }

    public Boolean getAllowSourcePathOverlap()
    {
        return allowSourcePathOverlap;
    }

    public Boolean getArchiveClassesAndAssets()
    {
        return archiveClassesAndAssets;
    }

    public Boolean getAs3()
    {
        return as3;
    }

    public Boolean getBenchmark()
    {
        return benchmark;
    }

    public Integer getBenchmarkCompilerDetails()
    {
        if ( benchmarkCompilerDetails == null )
        {
            return null;
        }

        if ( benchmarkCompilerDetails != 0 && benchmarkCompilerDetails != 1 && benchmarkCompilerDetails != 5 )
        {
            throw new IllegalArgumentException( "Invalid benchmarck compiler details level: '"
                + benchmarkCompilerDetails + "', it does accept 0 = none, 1 = light, 5 = verbose" );
        }

        return benchmarkCompilerDetails;
    }

    public Long getBenchmarkTimeFilter()
    {
        return benchmarkTimeFilter;
    }

    public Map<String, Object> getCache()
    {
        return cache;
    }

    public String getClassifier()
    {
        return classifier;
    }

    public String getCompatibilityVersion()
    {
        return compatibilityVersion;
    }

    protected Collection<Artifact> getCompiledResouceBundles()
    {
        if ( this.getLocale() == null )
        {
            return null;
        }

        Collection<Artifact> rbsSwc = new LinkedHashSet<Artifact>();

        Set<Artifact> beacons = getDependencies( type( RB_SWC ) );

        for ( String locale : getLocale() )
        {
            for ( Artifact beacon : beacons )
            {
                Artifact rbSwc =
                    resolve( beacon.getGroupId(), beacon.getArtifactId(), beacon.getVersion(), locale, beacon.getType() );
                rbsSwc.add( rbSwc );
            }
        }

        return rbsSwc;
    }

    public ICompilerConfiguration getCompilerConfiguration()
    {
        return this;
    }

    public Boolean getConservative()
    {
        return conservative;
    }

    public String getContextRoot()
    {
        return contextRoot;
    }

    public String[] getContributor()
    {
        if ( this.metadata != null )
        {
            return this.metadata.getContributor();
        }

        List<Contributor> contributors = project.getContributors();
        if ( contributors == null || contributors.isEmpty() )
        {
            return null;
        }

        String[] contributorsName = new String[contributors.size()];
        for ( int i = 0; i < contributorsName.length; i++ )
        {
            contributorsName[i] = contributors.get( i ).getName();
        }

        return contributorsName;
    }

    public String[] getCreator()
    {
        if ( this.metadata != null )
        {
            return this.metadata.getCreator();
        }

        List<Developer> developers = project.getDevelopers();
        if ( developers == null || developers.isEmpty() )
        {
            return null;
        }

        String[] creatorsName = new String[developers.size()];
        for ( int i = 0; i < creatorsName.length; i++ )
        {
            creatorsName[i] = developers.get( i ).getName();
        }

        return creatorsName;
    }

    public String getDate()
    {
        if ( this.metadata != null && this.metadata.getDate() != null )
        {
            return this.metadata.getDate();
        }

        return DATE_FORMAT.format( new Date() );
    }

    public Boolean getDebug()
    {
        return debug;
    }

    public String getDebugPassword()
    {
        return debugPassword;
    }

    public Integer getDefaultBackgroundColor()
    {
        return defaultBackgroundColor;
    }

    public Integer getDefaultFrameRate()
    {
        return defaultFrameRate;
    }

    public IDefaultScriptLimits getDefaultScriptLimits()
    {
        return defaultScriptLimits;
    }

    public List<String> getDefaultsCssFiles()
    {
        return PathUtil.getCanonicalPathList( defaultsCssFiles );
    }

    public String getDefaultsCssUrl()
    {
        return defaultsCssUrl;
    }

    public IDefaultSize getDefaultSize()
    {
        return defaultSize;
    }

    public IDefine[] getDefine()
    {
        if ( defines == null )
        {
            return null;
        }

        List<IDefine> keys = new ArrayList<IDefine>();
        Set<Entry<Object, Object>> entries = this.defines.entrySet();
        for ( final Entry<Object, Object> entry : entries )
        {
            keys.add( new IDefine()
            {
                public String name()
                {
                    return entry.getKey().toString();
                }

                public String value()
                {
                    return entry.getValue().toString();
                }
            } );
        }

        return keys.toArray( new IDefine[keys.size()] );
    }

    public Set<Artifact> getDependencies()
    {
        return Collections.unmodifiableSet( project.getArtifacts() );
    }

    protected Set<Artifact> getDependencies( Matcher<? extends Artifact>... matchers )
    {
        Set<Artifact> dependencies = getDependencies();

        return new LinkedHashSet<Artifact>( filter( allOf( matchers ), dependencies ) );
    }

    protected Artifact getDependency( Matcher<? extends Artifact>... matchers )
    {

        Set<Artifact> dependencies = getDependencies();
        List<Artifact> filtered = filter( allOf( matchers ), dependencies );
        if ( filtered.isEmpty() )
        {
            return null;
        }

        return filtered.get( 0 );
    }

    public String getDescription()
    {
        if ( this.metadata != null )
        {
            return this.metadata.getDescription();
        }

        return project.getDescription();
    }

    public Boolean getDisableIncrementalOptimizations()
    {
        return disableIncrementalOptimizations;
    }

    public Boolean getDoc()
    {
        return doc;
    }

    public String getDumpConfig()
    {
        return PathUtil.getCanonicalPath( dumpConfig );
    }

    public Boolean getEnableRuntimeDesignLayers()
    {
        return enableRuntimeDesignLayers;
    }

    public Boolean getEnableSwcVersionFiltering()
    {
        return enableSwcVersionFiltering;
    }

    public Boolean getEs()
    {
        return es;
    }

    public IExtension[] getExtension()
    {
        if ( extensions == null )
        {
            return null;
        }

        IExtension[] extensions = new IExtension[this.extensions.length];
        for ( int i = 0; i < extensions.length; i++ )
        {
            final MavenExtension extension = this.extensions[i];

            if ( extension.getExtensionArtifact() == null )
            {
                throw new IllegalArgumentException( "Extension artifact is required!" );
            }

            extensions[i] = new IExtension()
            {
                public File extension()
                {
                    MavenArtifact a = extension.getExtensionArtifact();
                    Artifact resolvedArtifact =
                        resolve( a.getGroupId(), a.getArtifactId(), a.getVersion(), a.getClassifier(), a.getType() );
                    return resolvedArtifact.getFile();
                }

                public String[] parameters()
                {
                    return extension.getParameters();
                }
            };
        }

        return extensions;
    }

    public IExtensionsConfiguration getExtensionsConfiguration()
    {
        return this;
    }

    @SuppressWarnings( "unchecked" )
    public File[] getExternalLibraryPath()
    {
        if ( SWC.equals( getProjectType() ) )
        {
            Matcher<? extends Artifact> swcs = allOf( type( SWC ), // 
                                                      anyOf( scope( EXTERNAL ), scope( COMPILE ), scope( null ) )//
                );
            return MavenUtils.getFiles( getDependencies( swcs, not( GLOBAL_MATCHER ) ), getGlobalArtifact() );
        }
        else
        {
            return MavenUtils.getFiles( getDependencies( not( GLOBAL_MATCHER ),// 
                                                         allOf( type( SWC ), scope( EXTERNAL ) ) ), getGlobalArtifact() );
        }
    }

    public List<String> getExterns()
    {
        if ( externs == null )
        {
            return null;
        }

        return Arrays.asList( externs );
    }

    public String getFinalName()
    {
        if ( finalName == null )
        {
            String c = getClassifier() == null ? "" : "-" + getClassifier();
            return project.getBuild().getFinalName() + c;
        }

        return finalName;
    }

    public Boolean getFlashType()
    {
        return flashType;
    }

    public String getFlexVersion()
    {
        Artifact compiler = MavenUtils.searchFor( pluginArtifacts, "com.adobe.flex", "compiler", null, "pom", null );
        return compiler.getVersion();
    }

    public IFontsConfiguration getFontsConfiguration()
    {
        return this;
    }

    public IFrame[] getFrame()
    {
        return frames;
    }

    public IFramesConfiguration getFramesConfiguration()
    {
        return this;
    }

    public String getFramework()
    {
        return framework;
    }

    // TODO lazy load here would be awesome
    protected Artifact getFrameworkConfig()
    {
        Artifact frmkCfg =
            getDependency( groupId( FRAMEWORK_GROUP_ID ), artifactId( "framework" ), classifier( "configs" ),
                           type( "zip" ) );

        // not on dependency list, trying to resolve it manually
        if ( frmkCfg == null )
        {
            // it should resolve playerglobal or airglobal, framework can be absent
            Artifact frmk = getDependency( groupId( FRAMEWORK_GROUP_ID ), artifactId( "framework" ) );

            if ( frmk == null )
            {
                return null;
            }

            frmkCfg = resolve( FRAMEWORK_GROUP_ID, "framework", frmk.getVersion(), "configs", "zip" );
        }
        return frmkCfg;
    }

    public Boolean getGenerateAbstractSyntaxTree()
    {
        return generateAbstractSyntaxTree;
    }

    public Boolean getGenerateFrameLoader()
    {
        return generateFrameLoader;
    }

    @SuppressWarnings( "unchecked" )
    public Collection<Artifact> getGlobalArtifact()
    {
        Artifact global = getDependency( GLOBAL_MATCHER );
        if ( global == null )
        {
            throw new IllegalArgumentException(
                                                "Global artifact is not available. Make sure to add 'playerglobal' or 'airglobal' to this project." );
        }

        File dir = new File( outputDirectory, "swcs" );
        dir.mkdirs();

        File source = global.getFile();
        File dest = new File( dir, global.getArtifactId() + "." + SWC );

        try
        {
            if ( !PathUtil.getCanonicalFile( source ).equals( PathUtil.getCanonicalFile( dest ) ) )
            {
                getLog().debug( "Striping global artifact, source: " + source + ", dest: " + dest );
                FileUtils.copyFile( source, dest );
            }
        }
        catch ( IOException e )
        {
            throw new IllegalStateException( "Error renamming '" + global.getArtifactId() + "'.", e );
        }

        global.setFile( dest );

        return Collections.singletonList( global );
    }

    public Boolean getHeadlessServer()
    {
        if ( headlessServer == null )
        {
            return GraphicsEnvironment.isHeadless();
        }

        return headlessServer;
    }

    public final String[] getHelp()
    {
        // must return null, otherwise will prevent compiler execution
        return null;
    }

    @SuppressWarnings( "unchecked" )
    public File[] getIncludeLibraries()
    {
        return MavenUtils.getFiles( getDependencies( type( SWC ), scope( INTERNAL ), not( GLOBAL_MATCHER ) ) );
    }

    public List<String> getIncludes()
    {
        if ( includes == null )
        {
            return null;
        }
        return Arrays.asList( includes );
    }

    public Boolean getIncremental()
    {
        return incremental;
    }

    public Boolean getIsolateStyles()
    {
        return isolateStyles;
    }

    public Boolean getKeepAllTypeSelectors()
    {
        return keepAllTypeSelectors;
    }

    public String[] getKeepAs3Metadata()
    {
        return keepAs3Metadatas;
    }

    public Boolean getKeepGeneratedActionscript()
    {
        return keepGeneratedActionscript;
    }

    public Boolean getKeepGeneratedSignatures()
    {
        return keepGeneratedSignatures;
    }

    public String[] getLanguage()
    {
        if ( this.metadata != null )
        {
            return this.metadata.getLanguage();
        }

        return getLocale();
    }

    public ILanguageRange[] getLanguageRange()
    {
        if ( licenses == null )
        {
            return null;
        }

        List<ILanguageRange> keys = new ArrayList<ILanguageRange>();
        Set<Entry<String, String>> entries = this.languageRange.entrySet();
        for ( final Entry<String, String> entry : entries )
        {
            keys.add( new ILanguageRange()
            {
                public String lang()
                {
                    return entry.getKey();
                }

                public String range()
                {
                    return entry.getValue();
                }
            } );
        }

        return keys.toArray( new ILanguageRange[keys.size()] );
    }

    public ILanguages getLanguagesConfiguration()
    {
        return this;
    }

    public Boolean getLazyInit()
    {
        return lazyInit;
    }

    @SuppressWarnings( "unchecked" )
    public File[] getLibraryPath()
    {
        if ( SWC.equals( getProjectType() ) )
        {
            return MavenUtils.getFiles( getDependencies( type( SWC ), scope( MERGED ), not( GLOBAL_MATCHER ) ),
                                        getCompiledResouceBundles() );
        }
        else
        {
            return MavenUtils.getFiles( getDependencies( type( SWC ),//
                                                         anyOf( scope( MERGED ), scope( COMPILE ), scope( null ) ),//
                                                         not( GLOBAL_MATCHER ) ),//
                                        getCompiledResouceBundles() );
        }
    }

    public ILicense[] getLicense()
    {
        if ( licenses == null )
        {
            return null;
        }

        List<ILicense> keys = new ArrayList<ILicense>();
        Set<Entry<String, String>> entries = this.licenses.entrySet();
        for ( final Entry<String, String> entry : entries )
        {
            keys.add( new ILicense()
            {
                public String product()
                {
                    return entry.getKey();
                }

                public String serialNumber()
                {
                    return entry.getValue();
                }
            } );
        }

        return keys.toArray( new ILicense[keys.size()] );
    }

    public ILicensesConfiguration getLicensesConfiguration()
    {
        return this;
    }

    public String getLinkReport()
    {
        File linkReport = new File( getTargetDirectory(), getFinalName() + "-" + LINK_REPORT + "." + XML );
        if ( linkReportAttach )
        {
            if ( getClassifier() != null )
            {
                getLog().warn( "Link report is not attached for artifacts with classifier" );
            }
            else
            {
                projectHelper.attachArtifact( project, XML, LINK_REPORT, linkReport );
            }
        }
        return PathUtil.getCanonicalPath( linkReport );
    }

    public String[] getLoadConfig()
    {
        return PathUtil.getCanonicalPath( ConfigurationResolver.resolveConfiguration( loadConfigs, loadConfig,
                                                                                      configDirectory ) );
    }

    @SuppressWarnings( "unchecked" )
    public String[] getLoadExterns()
    {
        if ( loadExterns == null )
        {
            Set<Artifact> dependencies = getDependencies( classifier( LINK_REPORT ), type( XML ) );

            if ( dependencies.isEmpty() )
            {
                return null;
            }

            return PathUtil.getCanonicalPath( MavenUtils.getFilesSet( dependencies ) );
        }
        return PathUtil.getCanonicalPath( loadExterns );
    }

    public String[] getLocale()
    {
        if ( compilerLocales != null )
        {
            return compilerLocales;
        }

        if ( runtimeLocales != null || SWC.equals( getProjectType() ) )
        {
            return null;
        }

        return new String[] { getToolsLocale() };
    }

    public List<String> getLocalFontPaths()
    {
        return PathUtil.getCanonicalPathList( localFontPaths );
    }

    public String getLocalFontsSnapshot()
    {
        if ( localFontsSnapshot != null )
        {
            return PathUtil.getCanonicalPath( localFontsSnapshot );
        }

        URL url;
        if ( MavenUtils.isMac() )
        {
            url = getClass().getResource( "/fonts/macFonts.ser" );
        }
        else if ( MavenUtils.isWindows() )
        {
            url = getClass().getResource( "/fonts/winFonts.ser" );
        }
        else
        {
            url = getClass().getResource( "/fonts/localFonts.ser" );
        }

        File fontsSer = new File( outputDirectory, "fonts.ser" );
        try
        {
            FileUtils.copyURLToFile( url, fontsSer );
        }
        catch ( IOException e )
        {
            throw new IllegalStateException( "Error copying fonts file.", e );
        }
        return PathUtil.getCanonicalPath( fontsSer );
    }

    public ILocalizedDescription[] getLocalizedDescription()
    {
        if ( this.metadata != null )
        {
            return this.metadata.getLocalizedDescription();
        }

        return null;
    }

    public ILocalizedTitle[] getLocalizedTitle()
    {
        if ( this.metadata != null )
        {
            return this.metadata.getLocalizedTitle();
        }

        return null;
    }

    public Log getLog()
    {
        return this.log;
    }

    public List<String> getManagers()
    {
        return managers;
    }

    public Logger getMavenLogger()
    {
        return new OEMLogAdapter( new MavenLogger( getLog() ) );
    }

    public SinglePathResolver getMavenPathResolver()
    {
        return new MavenPathResolver( resources );
    }

    public String getMaxCachedFonts()
    {
        if ( maxCachedFonts == null )
        {
            return null;
        }
        return maxCachedFonts.toString();
    }

    public String getMaxGlyphsPerFace()
    {
        if ( maxGlyphsPerFace == null )
        {
            return null;
        }
        return maxGlyphsPerFace.toString();
    }

    public Integer getMemoryUsageFactor()
    {
        return memoryUsageFactor;
    }

    public IMetadataConfiguration getMetadataConfiguration()
    {
        return this;
    }

    public String getMinimumSupportedVersion()
    {
        return this.minimumSupportedVersion;
    }

    public IMxmlConfiguration getMxmlConfiguration()
    {
        return this;
    }

    public INamespace[] getNamespace()
    {
        List<INamespace> namespaces = new ArrayList<INamespace>();
        if ( this.namespaces != null )
        {
            namespaces.addAll( Arrays.asList( this.namespaces ) );
        }

        File dir = getUnpackedFrameworkConfig();
        Reader cfg = null;
        try
        {
            cfg = new FileReader( new File( dir, "flex-config.xml" ) );

            Xpp3Dom dom = Xpp3DomBuilder.build( cfg );

            dom = dom.getChild( "compiler" );

            dom = dom.getChild( "namespaces" );

            Xpp3Dom[] defaultNamespaces = dom.getChildren();
            for ( Xpp3Dom xpp3Dom : defaultNamespaces )
            {
                String uri = xpp3Dom.getChild( "uri" ).getValue();
                String manifestName = xpp3Dom.getChild( "manifest" ).getValue();
                File manifest = new File( dir, manifestName );

                namespaces.add( new MavenNamespace( uri, manifest ) );
            }
        }
        catch ( Exception e )
        {
            throw new MavenRuntimeException( "Unable to retrieve flex default namespaces!", e );
        }
        finally
        {
            IOUtil.close( cfg );
        }

        return namespaces.toArray( new INamespace[0] );
    }

    public INamespacesConfiguration getNamespacesConfiguration()
    {
        return this;
    }

    protected List<String> getNamespacesUri()
    {
        if ( namespaces == null || namespaces.length == 0 )
        {
            return null;
        }

        List<String> uris = new ArrayList<String>();
        for ( INamespace namespace : namespaces )
        {
            uris.add( namespace.uri() );
        }

        return uris;
    }

    public Boolean getOmitTraceStatements()
    {
        return omitTraceStatements;
    }

    public Boolean getOptimize()
    {
        return optimize;
    }

    public String getOutput()
    {
        File output;
        if ( this.output != null )
        {
            output = this.output;
        }
        else
        {
            output = new File( getTargetDirectory(), getFinalName() + "." + getProjectType() );
        }

        output.getParentFile().mkdirs();

        if ( getClassifier() != null )
        {
            projectHelper.attachArtifact( project, getProjectType(), getClassifier(), output );
        }
        else
        {
            project.getArtifact().setFile( output );
        }

        return PathUtil.getCanonicalPath( output );
    }

    public String getProjectType()
    {
        return packaging;
    }

    public String[] getPublisher()
    {
        if ( this.metadata != null )
        {
            return this.metadata.getPublisher();
        }

        return getCreator();
    }

    public Boolean getQualifiedTypeSelectors()
    {
        return qualifiedTypeSelectors;
    }

    public String getRawMetadata()
    {
        return rawMetadata;
    }

    public Boolean getReportInvalidStylesAsWarnings()
    {
        return compilerWarnings.get( "report-invalid-styles-as-warnings" );
    }

    public String getResourceBundleList()
    {
        return PathUtil.getCanonicalPath( getResourceBundleListFile() );
    }

    protected List<String> getResourceBundleListContent()
    {
        String bundles;
        try
        {
            bundles = FileUtils.fileRead( getResourceBundleListFile() );
        }
        catch ( IOException e )
        {
            throw new MavenRuntimeException( e );
        }

        return Arrays.asList( bundles.substring( 10 ).split( " " ) );
    }

    /**
     * File content sample:
     * 
     * <pre>
     * bundles = containers core effects skins styles
     * </pre>
     * 
     * @return bundle list file
     */
    protected File getResourceBundleListFile()
    {
        if ( resourceBundleList != null )
        {
            return resourceBundleList;
        }

        if ( runtimeLocales == null )
        {
            return null;
        }

        defaultResourceBundleList.getParentFile().mkdirs();
        return defaultResourceBundleList;
    }

    public Boolean getResourceHack()
    {
        return resourceHack;
    }

    public String[] getRuntimeSharedLibraries()
    {
        return runtimeSharedLibraries;
    }

    public IRuntimeSharedLibraryPath[] getRuntimeSharedLibraryPath()
    {
        // TODO Auto-generated method stub
        return null;
    }

    public String getServices()
    {
        if ( services != null )
        {
            return PathUtil.getCanonicalPath( services );
        }

        File cfg = new File( configDirectory, "services-config.xml" );
        if ( cfg.exists() )
        {
            return PathUtil.getCanonicalPath( cfg );
        }
        return null;
    }

    public Boolean getShowActionscriptWarnings()
    {
        return compilerWarnings.get( "show-actionscript-warnings" );
    }

    public Boolean getShowBindingWarnings()
    {
        return compilerWarnings.get( "show-binding-warnings" );
    }

    public Boolean getShowDependencyWarnings()
    {
        return compilerWarnings.get( "show-dependency-warnings" );
    }

    public Boolean getShowDeprecationWarnings()
    {
        return compilerWarnings.get( "show-deprecation-warnings" );
    }

    public Boolean getShowInvalidCssPropertyWarnings()
    {
        return compilerWarnings.get( "show-invalid-css-property-warnings" );
    }

    public Boolean getShowShadowedDeviceFontWarnings()
    {
        return compilerWarnings.get( "show-shadowed-device-font-warnings" );
    }

    public Boolean getShowUnusedTypeSelectorWarnings()
    {
        return compilerWarnings.get( "show-unused-type-selector-warnings" );
    }

    public File getSignatureDirectory()
    {
        return signatureDirectory;
    }

    public File[] getSourcePath()
    {
        return PathUtil.getFiles( compileSourceRoots );
    }

    public Boolean getStaticLinkRuntimeSharedLibraries()
    {
        return staticLinkRuntimeSharedLibraries;
    }

    public Boolean getStrict()
    {
        return strict;
    }

    public Boolean getSwcChecksum()
    {
        return swcChecksum;
    }

    public File getTargetDirectory()
    {
        targetDirectory.mkdirs();
        return targetDirectory;
    }

    public String getTargetPlayer()
    {
        return targetPlayer;
    }

    public List<String> getTheme()
    {
        List<String> themes = new ArrayList<String>();
        if ( this.themes != null )
        {
            themes.addAll( PathUtil.getCanonicalPathList( this.themes ) );
        }
        themes.addAll( PathUtil.getCanonicalPathList( //
        MavenUtils.getFiles( getDependencies( anyOf( type( SWC ), type( CSS ) ),//
                                              scope( THEME ) ) ) ) );
        return themes;
    }

    public String getTitle()
    {
        if ( this.metadata != null )
        {
            return this.metadata.getDescription();
        }

        return project.getName();
    }

    public String getToolsLocale()
    {
        if ( toolsLocale == null )
        {
            throw new IllegalArgumentException( "Invalid toolsLocale it must be not null and must be in Java format."
                + "  For example, \"en\" or \"ja_JP\"" );
        }

        return toolsLocale;
    }

    public String getTranslationFormat()
    {
        return translationFormat;
    }

    // TODO lazy load here would be awesome
    protected File getUnpackedFrameworkConfig()
    {
        Artifact frmkCfg = getFrameworkConfig();

        File cfgZip = frmkCfg.getFile();
        File dest = new File( outputDirectory, "configs" );
        dest.mkdirs();

        try
        {
            UnArchiver unzip = archiverManager.getUnArchiver( cfgZip );
            unzip.setSourceFile( cfgZip );
            unzip.setDestDirectory( dest );
            unzip.extract();
        }
        catch ( Exception e )
        {
            throw new MavenRuntimeException( "Failed to unpack framework configuration", e );
        }

        return dest;
    }

    public Boolean getUseNetwork()
    {
        return userNetwork;
    }

    public Boolean getUseResourceBundleMetadata()
    {
        return useResourceBundleMetadata;
    }

    public Boolean getVerboseStacktraces()
    {
        return verboseStacktraces;
    }

    public Boolean getVerifyDigests()
    {
        return verifyDigests;
    }

    public final Boolean getVersion()
    {
        // must return null, otherwise will prevent compiler execution
        return null;
    }

    public Boolean getWarnArrayTostringChanges()
    {
        return compilerWarnings.get( "warn-array-tostring-changes" );
    }

    public Boolean getWarnAssignmentWithinConditional()
    {
        return compilerWarnings.get( "warn-assignment-within-conditional" );
    }

    public Boolean getWarnBadArrayCast()
    {
        return compilerWarnings.get( "warn-bad-array-cast" );
    }

    public Boolean getWarnBadBoolAssignment()
    {
        return compilerWarnings.get( "warn-bad-bool-assignment" );
    }

    public Boolean getWarnBadDateCast()
    {
        return compilerWarnings.get( "warn-bad-date-cast" );
    }

    public Boolean getWarnBadEs3TypeMethod()
    {
        return compilerWarnings.get( "warn-bad-es3-type-method" );
    }

    public Boolean getWarnBadEs3TypeProp()
    {
        return compilerWarnings.get( "warn-bad-es3-type-prop" );
    }

    public Boolean getWarnBadNanComparison()
    {
        return compilerWarnings.get( "warn-bad-nan-comparison" );
    }

    public Boolean getWarnBadNullAssignment()
    {
        return compilerWarnings.get( "warn-bad-null-assignment" );
    }

    public Boolean getWarnBadNullComparison()
    {
        return compilerWarnings.get( "warn-bad-null-comparison" );
    }

    public Boolean getWarnBadUndefinedComparison()
    {
        return compilerWarnings.get( "warn-bad-undefined-comparison" );
    }

    public Boolean getWarnBooleanConstructorWithNoArgs()
    {
        return compilerWarnings.get( "warn-boolean-constructor-with-no-args" );
    }

    public Boolean getWarnChangesInResolve()
    {
        return compilerWarnings.get( "warn-changes-in-resolve" );
    }

    public Boolean getWarnClassIsSealed()
    {
        return compilerWarnings.get( "warn-class-is-sealed" );
    }

    public Boolean getWarnConstNotInitialized()
    {
        return compilerWarnings.get( "warn-const-not-initialized" );
    }

    public Boolean getWarnConstructorReturnsValue()
    {
        return compilerWarnings.get( "warn-constructor-returns-value" );
    }

    public Boolean getWarnDeprecatedEventHandlerError()
    {
        return compilerWarnings.get( "warn-deprecated-event-handler-error" );
    }

    public Boolean getWarnDeprecatedFunctionError()
    {
        return compilerWarnings.get( "warn-deprecated-function-error" );
    }

    public Boolean getWarnDeprecatedPropertyError()
    {
        return compilerWarnings.get( "warn-deprecated-property-error" );
    }

    public Boolean getWarnDuplicateArgumentNames()
    {
        return compilerWarnings.get( "warn-duplicate-argument-names" );
    }

    public Boolean getWarnDuplicateVariableDef()
    {
        return compilerWarnings.get( "warn-duplicate-variable-def" );
    }

    public Boolean getWarnForVarInChanges()
    {
        return compilerWarnings.get( "warn-for-var-in-changes" );
    }

    public Boolean getWarnImportHidesClass()
    {
        return compilerWarnings.get( "warn-import-hides-class" );
    }

    public Boolean getWarnings()
    {
        return warnings;
    }

    public Boolean getWarnInstanceOfChanges()
    {
        return compilerWarnings.get( "warn-instance-of-changes" );
    }

    public Boolean getWarnInternalError()
    {
        return compilerWarnings.get( "warn-internal-error" );
    }

    public Boolean getWarnLevelNotSupported()
    {
        return compilerWarnings.get( "warn-level-not-supported" );
    }

    public Boolean getWarnMissingNamespaceDecl()
    {
        return compilerWarnings.get( "warn-missing-namespace-decl" );
    }

    public Boolean getWarnNegativeUintLiteral()
    {
        return compilerWarnings.get( "warn-negative-uint-literal" );
    }

    public Boolean getWarnNoConstructor()
    {
        return compilerWarnings.get( "warn-no-constructor" );
    }

    public Boolean getWarnNoExplicitSuperCallInConstructor()
    {
        return compilerWarnings.get( "warn-no-explicit-super-call-in-constructor" );
    }

    public Boolean getWarnNoTypeDecl()
    {
        return compilerWarnings.get( "warn-no-type-decl" );
    }

    public Boolean getWarnNumberFromStringChanges()
    {
        return compilerWarnings.get( "warn-number-from-string-changes" );
    }

    public Boolean getWarnScopingChangeInThis()
    {
        return compilerWarnings.get( "warn-scoping-change-in-this" );
    }

    public Boolean getWarnSlowTextFieldAddition()
    {
        return compilerWarnings.get( "warn-slow-text-field-addition" );
    }

    public Boolean getWarnUnlikelyFunctionValue()
    {
        return compilerWarnings.get( "warn-unlikely-function-value" );
    }

    public Boolean getWarnXmlClassHasChanged()
    {
        return compilerWarnings.get( "warn-xml-class-has-changed" );
    }

    protected Artifact resolve( String groupId, String artifactId, String version, String classifier, String type )
    {
        Artifact artifact =
            repositorySystem.createArtifactWithClassifier( groupId, artifactId, version, type, classifier );
        if ( !artifact.isResolved() )
        {
            ArtifactResolutionRequest req = new ArtifactResolutionRequest();
            req.setArtifact( artifact );
            req.setLocalRepository( localRepository );
            req.setRemoteRepositories( remoteRepositories );
            // FIXME need to check isSuccess
            repositorySystem.resolve( req ).isSuccess();
        }
        return artifact;
    }

    protected DirectoryScanner scan( File directory, PatternSet pattern )
    {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( directory );
        scanner.setIncludes( (String[]) pattern.getIncludes().toArray( new String[0] ) );
        scanner.setExcludes( (String[]) pattern.getExcludes().toArray( new String[0] ) );
        scanner.addDefaultExcludes();
        scanner.scan();
        return scanner;
    }

    public void setLog( Log log )
    {
        this.log = log;
    }

    protected void wait( List<Result> results )
        throws MojoFailureException, MojoExecutionException
    {
        for ( Result result : results )
        {
            checkResult( result );
        }
    }
}
