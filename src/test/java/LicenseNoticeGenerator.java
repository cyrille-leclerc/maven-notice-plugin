import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class LicenseNoticeGenerator {

    private static Function<String, String> toLowerCase(){
        return new Function<String, String>() {
            @Override
            public String apply(String input) {
                return input.toLowerCase();
            }
        };
    }

    static class ArtifactDetails implements Comparable<ArtifactDetails> {
        static Function<ArtifactDetails, String> toLicenseFunction() {
            return new Function<ArtifactDetails, String>() {
                @Override
                public String apply(ArtifactDetails artifactDetails) {
                    return artifactDetails.license;
                }
            };

        }

        String title;
        String artifactId;
        String groupId;
        String license;
        String licenseUrl;

        public ArtifactDetails(String title, String artifactId, String groupId, String license, String licenseUrl) {
            this.title = title;
            this.artifactId = artifactId;
            this.groupId = groupId;
            this.license = license;
            this.licenseUrl = licenseUrl;
        }

        @Override
        public int compareTo(ArtifactDetails o) {
            return ComparisonChain.start().compare(this.groupId, o.groupId).compare(this.artifactId, o.artifactId).result();
        }

        @Override
        public String toString() {
            return "ArtifactDetails{" +
                    "title='" + title + '\'' +
                    ", artifactId='" + artifactId + '\'' +
                    ", groupId='" + groupId + '\'' +
                    ", license='" + license + '\'' +
                    ", licenseUrl='" + licenseUrl + '\'' +
                    '}';
        }
    }

    static class LicenseDetails {
        public static Function<LicenseDetails, List<String>> toGroupIdsFunction() {
            return new Function<LicenseDetails, List<String>>() {
                @Override
                public List<String> apply(LicenseDetails licenseDetails) {
                    return licenseDetails.groupIds;
                }
            };
        }

        public static Function<LicenseDetails, String> toArtifactIdFunction() {
            return new Function<LicenseDetails, String>() {
                @Override
                public String apply(LicenseDetails licenseDetails) {
                    return licenseDetails.artifactId;
                }
            };
        }

        public static Function<LicenseDetails, Set<String>> toNames() {
            return new Function<LicenseDetails, Set<String>>() {
                @Override
                public Set<String> apply(LicenseDetails licenseDetails) {
                    Set<String> names = new TreeSet<>();
                    names.add(licenseDetails.name);
                    names.addAll(licenseDetails.alliases);
                    return names;
                }
            };
        }

        String artifactId;
        List<String> groupIds;
        String name;
        Set<String> alliases;
        String content;

        public LicenseDetails(List<String> groupIds, String artifactId, String name, Set<String> alliases, String content) {
            this.groupIds = groupIds;
            this.artifactId = artifactId;
            this.name = name;
            this.alliases = alliases;
            this.content = content;
        }

        @Override
        public String toString() {
            return "LicenseDetails{" +
                    "groupIds='" + groupIds + '\'' +
                    ", artifactId='" + artifactId + '\'' +
                    ", name='" + name + '\'' +
                    ", alliases=" + alliases +
                    ", content='" + StringUtils.abbreviate(content, 10) + '\'' +
                    '}';
        }
    }

    public static void main(String[] args) throws Exception {

        List<String> errors = new ArrayList<>();

        List<ArtifactDetails> artifactsAsList = getArtifacts();
        Collections.sort(artifactsAsList);

        Collection<ArtifactDetails> artifacts = artifactsAsList;

        artifacts = Collections2.filter(artifacts, new Predicate<ArtifactDetails>() {
            @Override
            public boolean apply(ArtifactDetails artifactDetails) {
                return !"CloudBees License".equals(artifactDetails.license);
            }
        });

        List<LicenseDetails> licenses = getLicenses();

        for (LicenseDetails license : licenses) {
            // System.out.println(license);
        }

        File file = new File("LICENSES.txt");
        System.out.println("Generate " + file.getAbsolutePath());
        PrintWriter writer = new PrintWriter(new FileWriter(file));
        writer.println("LICENSES");
        TreeSet<String> distinctLicenses = Sets.newTreeSet(Collections2.transform(artifacts, ArtifactDetails.toLicenseFunction()));
        for (String license : distinctLicenses) {
            // System.out.println(license);
        }

        // System.out.println(distinctLicenses.size());

        for (ArtifactDetails artifact : artifacts) {
            LicenseDetails license = findLicense(artifact.groupId, artifact.artifactId, artifact.license, licenses);
            if (license == null) {
                String x = "NO LICENSE FOUND for artifact " + artifact.groupId + ":" + artifact.artifactId + " " + artifact.license;
                // System.err.println(x);
                errors.add(x);
            } else {
                writer.println("---");
                writer.println("# " + artifact.title + " (" + artifact.groupId + ":" + artifact.artifactId + ")");
                writer.println();
                writer.println("Licence name: " + license.name);
                writer.println("License:");
                writer.println(license.content);
                writer.println();
                writer.println();
            }
        }


        writer.flush();
        writer.close();
        System.out.println(file.getAbsolutePath() + " generated with the notice of " + artifacts.size() + " artifact documented");
        System.out.flush();
        System.err.println("ERRORS");
        Collections.sort(errors);
        for (String error : errors) {
            System.err.println(error);
        }
        System.err.println(errors.size() + "errors");

    }

    public static LicenseDetails findLicense(String groupId, String artifactId, String licenseName, List<LicenseDetails> licenses) throws Exception {


        Predicate<LicenseDetails> filterOnGroupIdAndLicense = new Predicate<LicenseDetails>() {
            @Override
            public boolean apply(LicenseDetails license) {
                boolean groupIdEquals = license.groupIds.contains(groupId);
                Set<String> names = LicenseDetails.toNames().apply(license);
                boolean nameEqual = Collections2.transform(names , LicenseNoticeGenerator.toLowerCase()).contains(licenseName.toLowerCase());
                return groupIdEquals && nameEqual;
            }
        };


        Predicate<LicenseDetails> filterOnArtifactFqdn = new Predicate<LicenseDetails>() {
            @Override
            public boolean apply(LicenseDetails license) {
                return license.groupIds.size() == 1 && groupId.equals(license.groupIds.get(0)) && artifactId.equals(license.artifactId);
            }
        };

        Predicate<LicenseDetails> filterOnLicenseNameForNonDiscriminantLicense = new Predicate<LicenseDetails>() {
            @Override
            public boolean apply(LicenseDetails license) {
                return license.groupIds.isEmpty() && license.artifactId == null && LicenseDetails.toNames().apply(license).contains(licenseName);
            }
        };


        Collection<LicenseDetails> matches;

        matches = Collections2.filter(licenses, filterOnArtifactFqdn);
        if (matches.size() == 1) {
            LicenseDetails onlyElement = Iterables.getOnlyElement(matches);
            // System.out.println(groupId + ":" + artifactId + " '" + licenseName + " match on artifact FQDN " + onlyElement);
            return onlyElement;
        } else if (matches.size() > 1) {
            throw new IllegalStateException("Found more than 1" + matches);
        }

        matches = Collections2.filter(licenses, filterOnGroupIdAndLicense);
        if (matches.size() == 1) {
            LicenseDetails onlyElement = Iterables.getOnlyElement(matches);
            // System.out.println(groupId + ":" + artifactId + " '" + licenseName + " match on groupId and License name " + onlyElement);
            return onlyElement;
        } else if (matches.size() > 1) {
            throw new IllegalStateException("Found more than 1" + matches);
        }
        LicenseDetails onlyElement = Iterables.getOnlyElement(Collections2.filter(licenses, filterOnLicenseNameForNonDiscriminantLicense), null);
        if (onlyElement == null) {
            return onlyElement;
        } else {
            //  System.out.println(groupId + ":" + artifactId + " '" + licenseName + " match on License name " + onlyElement);
            return onlyElement;
        }
    }

    protected static List<ArtifactDetails> getArtifacts() throws SAXException, IOException, ParserConfigurationException {
        String source = "application-licenses-cje-2.7.20.2.xml";
        System.out.println("Input: " + Thread.currentThread().getContextClassLoader().getResource(source));
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(source);
        Preconditions.checkNotNull(in);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document doc = dbf.newDocumentBuilder().parse(in);
        NodeList artifactNL = doc.getElementsByTagName("artifact");

        List<ArtifactDetails> artifacts = new ArrayList<>();
        for (int temp = 0; temp < artifactNL.getLength(); temp++) {

            Node nNode = artifactNL.item(temp);

            if (nNode.getNodeType() == Node.ELEMENT_NODE) {

                Element eElement = (Element) nNode;

                String artifactTitleAndId = getElementAsString(eElement, "artifactId");

                String artifactName = StringUtils.substringBeforeLast(artifactTitleAndId, "(").trim();

                String artifactFqdn = StringUtils.substringBefore(
                        StringUtils.substringAfterLast(artifactTitleAndId, "("),
                        ")");

                String groupId = StringUtils.substringBefore(artifactFqdn, ":");
                String artifactId = StringUtils.substringAfter(artifactFqdn, ":");


                String license = getElementAsString(eElement, "license");

                ArtifactDetails artifactDetails = new ArtifactDetails(artifactName, artifactId, groupId, license, null);
                artifacts.add(artifactDetails);
            }
        }

        return artifacts;
    }

    protected static List<LicenseDetails> getLicenses() throws SAXException, IOException, ParserConfigurationException {
        System.out.println("Licenses: " + Thread.currentThread().getContextClassLoader().getResource("licenses.xml"));
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("licenses.xml");
        Preconditions.checkNotNull(in);
        // ByteStreams.copy(in, System.out);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document doc = dbf.newDocumentBuilder().parse(in);
        NodeList licencesNL = doc.getElementsByTagName("license");

        List<LicenseDetails> licenses = new ArrayList<>();
        for (int temp = 0; temp < licencesNL.getLength(); temp++) {

            Node licenseNode = licencesNL.item(temp);

            if (licenseNode.getNodeType() == Node.ELEMENT_NODE) {

                Element licenseElement = (Element) licenseNode;

                String name = getElementAsString(licenseElement, "name");
                String content = getElementAsString(licenseElement, "content");
                List<String> groupIds = getElementsAsString(licenseElement, "groupId");
                String artifactId = getElementAsString(licenseElement, "artifactId");
                NodeList synonymsNL = licenseElement.getElementsByTagName("synonyms");
                Set<String> synonyms = new HashSet<>();
                if (synonymsNL.getLength() == 0) {

                } else {
                    NodeList synonymsChildrenNL = synonymsNL.item(0).getChildNodes();
                    for (int j = 0; j < synonymsChildrenNL.getLength(); j++) {
                        Node synonymNode = synonymsChildrenNL.item(j);
                        if (synonymNode.getNodeType() == Node.ELEMENT_NODE) {
                            synonyms.add(synonymNode.getTextContent());
                        }
                    }
                }

                licenses.add(new LicenseDetails(groupIds, artifactId, name, synonyms, content));
            }
        }

        return licenses;
    }

    private static String getElementAsString(Element elementName, String subElementName) {
        NodeList elementsByTagName = elementName.getElementsByTagName(subElementName);
        if (elementsByTagName.getLength() == 0)
            return null;
        return elementsByTagName.item(0).getTextContent();
    }
    private static List<String> getElementsAsString(Element elementName, String subElementName) {
        NodeList elementsByTagName = elementName.getElementsByTagName(subElementName);
        List<String> result = new ArrayList<>();
        for(int i = 0;i < elementsByTagName.getLength();i++) {
            result.add(elementsByTagName.item(i).getTextContent());
        }
        return result;
    }
}
