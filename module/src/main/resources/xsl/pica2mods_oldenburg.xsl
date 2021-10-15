<?xml version="1.0"?>
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:p="info:srw/schema/5/picaXML-v1.0"
                xmlns:mods="http://www.loc.gov/mods/v3"
                xmlns:xlink="http://www.w3.org/1999/xlink"
                exclude-result-prefixes="xsl p xlink">
    <xsl:mode on-no-match="shallow-copy"/>

    <xsl:import href="default/pica2mods-default-titleInfo.xsl"/>
    <xsl:import href="default/pica2mods-default-name.xsl"/>
    <xsl:import href="default/pica2mods-default-identifier.xsl"/>
    <xsl:import href="default/pica2mods-default-language.xsl"/>
    <xsl:import href="default/pica2mods-default-location.xsl"/>
    <xsl:import href="default/pica2mods-default-physicalDescription.xsl"/>
    <xsl:import href="default/pica2mods-default-originInfo.xsl"/>
    <xsl:import href="default/pica2mods-default-genre.xsl"/>
    <xsl:import href="default/pica2mods-default-recordInfo.xsl"/>
    <xsl:import href="default/pica2mods-default-note.xsl"/>
    <xsl:import href="default/pica2mods-default-abstract.xsl"/>
    <xsl:import href="default/pica2mods-default-subject.xsl"/>
    <xsl:import href="default/pica2mods-default-relatedItem.xsl"/>

    <xsl:import href="_common/pica2mods-pica-PREPROCESSING.xsl"/>
    <xsl:import href="_common/pica2mods-functions.xsl"/>

    <xsl:param name="MCR.PICA2MODS.CONVERTER_VERSION" select="'Pica2Mods 2.1'"/>
    <xsl:param name="MCR.PICA2MODS.DATABASE" select="'k10plus'"/>
    <xsl:param name="institute" />
    <xsl:param name="collection" />

    <xsl:template match="p:record">
        <mods:mods>
            <xsl:call-template name="modsTitleInfo"/>
            <xsl:call-template name="modsAbstract"/>
            <xsl:call-template name="modsName"/>
            <xsl:call-template name="modsIdentifier"/>
            <xsl:call-template name="modsLanguage"/>
            <xsl:call-template name="modsPhysicalDescription"/>
            <xsl:call-template name="modsOriginInfo"/>
            <xsl:call-template name="odbModsGenre"/>
            <xsl:call-template name="modsLocation"/>
            <xsl:call-template name="modsRecordInfo"/>
            <xsl:call-template name="modsNote"/>
            <xsl:call-template name="modsRelatedItem"/>
            <xsl:call-template name="odbModsSubjectCartographics"/>
            <xsl:call-template name="odbModsInstitution" />
            <xsl:call-template name="odbModsCollectionClass" />
            <xsl:call-template name="odbx001148Class" />
        </mods:mods>
    </xsl:template>

    <xsl:template name="odbx001148Class">
        <xsl:if test="count(p:datafield[@tag='034M']/p:subfield[@code='a'])&gt;0">
            <xsl:for-each select="p:datafield[@tag='034M']/p:subfield[@code='a']">
                <xsl:variable name="mappedValue">
                    <xsl:call-template name="mapMaterialValue">
                        <xsl:with-param name="value" select="text()" />
                    </xsl:call-template>
                </xsl:variable>
                <xsl:if test="$mappedValue!='UNDEFINED'">
                    <mods:classification displayLabel="illustration" authorityURI="http://kartenspeicher.gbv.de/classifications/x001148x" valueURI="http://kartenspeicher.gbv.de/classifications/x001148x#{$mappedValue}"/>
                </xsl:if>
            </xsl:for-each>
        </xsl:if>
    </xsl:template>

    <xsl:template name="odbModsGenre">
        <mods:genre type="intern" authorityURI="http://www.mycore.org/classifications/mir_genres" valueURI="http://www.mycore.org/classifications/mir_genres#map"/>
    </xsl:template>

    <xsl:template name="odbModsInstitution">
        <mods:name type="corporate" authorityURI="http://www.mycore.org/classifications/mir_institutes" valueURI="http://www.mycore.org/classifications/mir_institutes#{$institute}" xlink:type="simple">
            <mods:role>
                <mods:roleTerm authority="marcrelator" type="code">his</mods:roleTerm>
            </mods:role>
        </mods:name>
    </xsl:template>

    <xsl:template name="odbModsCollectionClass">
        <mods:classification authorityURI="http://kartenspeicher.gbv.de/mir/api/v1/classifications/collection" displayLabel="collection" valueURI="http://kartenspeicher.gbv.de/mir/api/v1/classifications/collection#{$collection}" />
    </xsl:template>

    <xsl:template name="odbModsSubjectCartographics">
        <xsl:variable name="scale" select="p:datafield[@tag='035E']/p:subfield[@code='g']"/>
        <xsl:variable name="coords"
                      select="p:datafield[@tag='035G']/p:subfield[@code='a' or @code='b' or @code='c' or @code='d']"/>

        <xsl:if test="string-length($scale) &gt; 0">
            <mods:subject>
                <mods:cartographics>
                    <xsl:if test="string-length($scale) &gt; 0">
                        <mods:scale>
                            <xsl:value-of select="$scale"/>
                        </mods:scale>
                    </xsl:if>
                    <xsl:if test="count($coords)">
                        <xsl:variable name="a" select="p:datafield[@tag='035G']/p:subfield[@code='a']"/>
                        <xsl:variable name="b" select="p:datafield[@tag='035G']/p:subfield[@code='b']"/>
                        <xsl:variable name="c" select="p:datafield[@tag='035G']/p:subfield[@code='c']"/>
                        <xsl:variable name="d" select="p:datafield[@tag='035G']/p:subfield[@code='d']"/>
                        <mods:coordinates><xsl:value-of select="concat($a, $b, $c, $d)"/></mods:coordinates> <!-- need to convert them in Java -->
                    </xsl:if>
                </mods:cartographics>
            </mods:subject>
        </xsl:if>
    </xsl:template>

    <xsl:template name="mapMaterialValue">
        <xsl:param name="value" />
        <xsl:choose>
            <xsl:when test="contains($value, ', Lith')">x001166x</xsl:when>
            <xsl:when test="contains($value, ', Lithogr')">x001166x</xsl:when>
            <xsl:when test="contains($value, 'Lith ;')">x001166x</xsl:when>
            <xsl:when test="contains($value, 'Lith. ,')">x001166x</xsl:when>
            <xsl:when test="contains($value, 'Lith.;')">x001166x</xsl:when>
            <xsl:when test="contains($value, 'Lith,')">x001166x</xsl:when>
            <xsl:when test="contains($value, 'Lith')">x001166x</xsl:when>
            <xsl:when test="contains($value, 'Lithogr.')">x001166x</xsl:when>
            <xsl:when test="contains($value, 'Lithogr.;')">x001166x</xsl:when>
            <xsl:when test="contains($value, 'Lithogr')">x001166x</xsl:when>
            <xsl:when test="contains($value, 'Lithographie;')">x001166x</xsl:when>
            <xsl:when test="contains($value, 'Lithograpphie;')">x001166x</xsl:when>
            <xsl:when test="contains($value, 'Lithographie')">x001166x</xsl:when>
            <xsl:when test="contains($value, 'lithografi')">x001166x</xsl:when>
            <xsl:when test="contains($value, 'Farblithogr')">x001166x</xsl:when>
            <xsl:when test="contains($value, 'farblithografi')">x001166x</xsl:when>
            <xsl:when test="contains($value, 'Handzeichnung')">x001235x</xsl:when>
            <xsl:when test="contains($value, 'Holzschnitt')">x001177x</xsl:when>
            <xsl:when test="contains($value, 'Holzschnitt ;')">x001177x</xsl:when>
            <xsl:when test="contains($value, 'holzschnitt')">x001177x</xsl:when>
            <xsl:when test="contains($value, 'kupferstich ,')">x001192x</xsl:when>
            <xsl:when test="contains($value, 'Kuperstich')">x001192x</xsl:when>
            <xsl:when test="contains($value, 'Kupferst.')">x001192x</xsl:when>
            <xsl:when test="contains($value, 'Kupferst.,')">x001192x</xsl:when>
            <xsl:when test="contains($value, 'Kupferstich')">x001192x</xsl:when>
            <xsl:when test="contains($value, 'kupfertstich')">x001192x</xsl:when>
            <xsl:when test="contains($value, 'Kupferstich ,')">x001192x</xsl:when>
            <xsl:when test="contains($value, 'Kupferstich ;')">x001192x</xsl:when>
            <xsl:when test="contains($value, 'Kupferstich,')">x001192x</xsl:when>
            <xsl:when test="contains($value, 'Kupferstich.')">x001192x</xsl:when>
            <xsl:when test="contains($value, 'Kupfestich')">x001192x</xsl:when>
            <xsl:when test="contains($value, 'Kupferst')">x001192x</xsl:when>
            <xsl:when test="contains($value, 'Radierung')">x001196x</xsl:when>
            <xsl:when test="contains($value, 'radierung')">x001196x</xsl:when>
            <xsl:when test="contains($value, 'Stahlstich')">x001202x</xsl:when>
            <xsl:when test="contains($value, 'stahlstich')">x001202x</xsl:when>
            <xsl:when test="contains($value, 'Zinkdruck')">ts005001</xsl:when>
            <xsl:when test="contains($value, 'Zinkogr')">ts005001</xsl:when>
            <xsl:when test="contains($value, 'Zinkographie')">ts005001</xsl:when>
            <xsl:when test="contains($value, 'einfarb.')">lb000025</xsl:when>
            <xsl:when test="contains($value, 'einfarbig')">lb000025</xsl:when>
            <xsl:when test="contains($value, 'farb.')">tr000029</xsl:when>
            <xsl:when test="contains($value, 'mehrfarb.')">tr000029</xsl:when>
            <xsl:when test="contains($value, 'mehrfarbig')">tr000029</xsl:when>
            <xsl:when test="contains($value, 'schw.-weiß')">lb000025</xsl:when>
            <xsl:otherwise>UNDEFINED</xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>