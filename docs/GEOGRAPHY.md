# County, sub-county and ward reference data

The current master is `src/main/resources/geography.yml`: 47 counties and 333
sub-counties, with the supplied 2009 and 2019 census populations. It uses the
user-supplied [census table](https://www.citypopulation.de/en/kenya/sub/admin/)
for names, county associations and counts. County codes are cross-checked against
the [provided gist](https://gist.github.com/Aroniez/a6b4e380bc7b9abdf595470749fb68cc);
all 47 county names/codes match after normalising `Trans-Nzoia` to `Trans Nzoia`.
The gist is not an authoritative gazette and appears to mix constituencies with
sub-counties. Its Kisii list is empty, while the census list has 11 sub-counties.

The YAML also contains a `constituencies` collection under each county; each entry
has a source `subcounty_id`, name, and ward-name list. The source ID is retained as
input metadata only.

The single `GeographySeeder` loads counties, administrative sub-counties, and
wards. It correlates each constituency entry to a sub-county only by an exact,
case-sensitive name match within the same county. It does not use or translate
`subcounty_id`, and it does not normalize whitespace, punctuation, or spelling.
Matched wards are stored with an integer foreign key to `sub_counties`; unmatched
groups and their wards are skipped and reported in the startup summary.

For the current YAML, **175 of 288 constituency groups match exactly** and 854
ward names are eligible for seeding. **113 groups do not match**, so **549 ward
names are not picked up**. Four exact-matching groups have an empty ward list.
The names below are the constituency entries that need review against their
county’s `sub-counties` before their wards can be linked:

| County | Unmatched constituency names |
|---|---|
| Baringo | `Baringo  North`, `Baringo South`, `Eldama Ravine` |
| Bomet | `Konoin` |
| Bungoma | `Sirisia`, `Kabuchai`, `Kanduyi`, `Webuye East`, `Kimilili` |
| Busia | `Matayos`, `Funyula`, `Budalangi` |
| Embu | `Manyatta`, `Runyenjes` |
| Garissa | `Garissa Township` |
| Homa Bay | `Kasipul`, `Kabondo Kasipul`, `Karachuonyo`, `Homa Bay Town`, `Mbita`, `Suba` |
| Isiolo | `Isiolo North`, `Isiolo South` |
| Kajiado | `Kajiado East`, `Kajiado South` |
| Kakamega | `Malava`, `Lurambi`, `Shinyalu`, `Ikolomani` |
| Kericho | `Kipkelion East`, `Kipkelion West`, `Ainamoi`, `Sigowet/Soin` |
| Kiambu | `Thika Town` |
| Kirinyaga | `Mwea`, `Gichugu`, `Ndia` |
| Kisii | `Bonchari`, `South Mugirango`, `Bomachoge Borabu`, `Bobasi`, `Bomachoge Chache`, `Nyaribari Masaba`, `Nyaribari Chache`, `Kitutu Chache North`, `Kitutu Chache South` |
| Kitui | `Mwingi North`, `Mwingi West`, `Kitui Rural`, `Kitui East`, `Kitui South` |
| Kwale | `Lungalunga` |
| Machakos | `Mavoko`, `Machakos Town` |
| Makueni | `Mbooni`, `Kilome`, `Kaiti`, `Kibwezi West`, `Kibwezi East` |
| Mandera | `Banissa`, `Mandera South` |
| Marsabit | `Saku`, `Laisamis` |
| Meru | `North Imenti`, `Buuri`, `Central Imenti`, `South Imenti` |
| Murang'a | `Kiharu`, `Maragwa` |
| Nairobi | `Dagoretti North`, `Dagoretti South`, `Langata`, `Roysambu`, `Ruaraka`, `Embakasi South`, `Embakasi North`, `Embakasi Central`, `Embakasi East`, `Embakasi West` |
| Nakuru | `Bahati`, `Nakuru Town West`, `Nakuru Town East` |
| Nandi | `Aldai`, `Nandi Hills`, `Emgwen`, `Mosop` |
| Narok | `Kilgoris`, `Emurua Dikirr`, `Narok North` |
| Nyamira | `Kitutu Masaba`, `West Mugirango`, `North Mugirango` |
| Nyandarua | `Ol Kalou`, `Ol Jorok`, `Ndaragwa` |
| Nyeri | `Kieni`, `Mathira`, `Othaya`, `Nyeri Town` |
| Samburu | `Samburu West` |
| Siaya | `Alego Usonga` |
| Taita-Taveta | `Wundanyi` |
| Tana River | `Garsen`, `Galole`, `Bura` |
| Tharaka-Nithi | `Chuka/Igambang'Om`, `Tharaka` |
| Trans Nzoia | `Saboti`, `Cherangany` |
| Vihiga | `Hamisi` |
| West Pokot | `Kapenguria`, `Sigor`, `Kacheliba` |

These counts describe the current YAML and will change when its source is
reviewed. This is a known **data gap and technical debt**, not a fuzzy-matching
problem to solve in code: the source names come from different datasets, and
automatically normalizing or guessing could link a ward to the wrong service
area. Reconcile names with the data owner and update the YAML explicitly.

The sources differ in numerous places: e.g. Nairobi's census list has `Dagoretti`
and `Embakasi` while the gist splits them into several named areas; the gist has
`Nambele`, `Genzw` and `Mohoroni`, while the census lists `Nambale`, `Ganze` and
`Muhoroni`. Some differences may be typos, renamed areas, different administrative
levels or boundary changes. No equivalence or boundary mapping is inferred.
[The full lexical comparison](GEOGRAPHY_SOURCE_COMPARISON.md) shows unmatched names
in both directions. National parks and the Kenya total are excluded because they
are not counties or sub-counties. Parenthetical qualifiers from the census are
preserved in names; populations are historical counts, not current estimates.
The source contains historical 2019 census boundaries and must be reconciled with
current official administrative records before production use.

Flyway creates integer-keyed `counties`, `sub_counties` and `wards` tables. The
single `GeographySeeder` validates the YAML and applies the reference seed in one
transaction at startup. County IDs are the supplied codes; sub-county IDs are
generated and stable across restarts; ward rows reference the matched sub-county.
Existing seed rows are updated or retained by their county/name or
sub-county/name identity; extra database rows are not deleted. A county ID/name
conflict or invalid seed stops startup. Renaming a sub-county in the YAML creates
a new row rather than silently reassigning an existing identity. Such changes
require an explicit reviewed migration and reference reconciliation. Concurrent
startup can conflict/deadlock: deploy one migration/seeding instance at a time.

The seed writes reference data through JDBC intentionally: startup has no
authenticated user, while JPA `Base` auditing rejects unauthenticated writes.
These tables are lookup data, not JPA entities, and have no misleading user audit
actor. Do not expose direct reference-table writes to normal users.

Public, read-only cascading lookup endpoints (no bearer token required):

- `GET /api/geography/counties` returns paginated `id` and `name` values.
- `GET /api/geography/counties/{countyId}/sub-counties` returns
  `subCountyId`, `subCountyName`, and `countyName`; an unknown county returns 404.
- `GET /api/geography/counties/{countyId}/sub-counties/{subCountyId}/wards`
  returns `countyName`, `subCountyName`, `wardName`, and `wardId` only when the
  sub-county belongs to that county.

All collection endpoints accept optional `pageNo`, `pageSize`, `sortBy`, and
`sortDirection` parameters. Defaults are page 0, page size 2, and descending name
for reference lookups; page size is capped at 100.

Results are alphabetically sorted and use the standard response wrapper.
Only the three GET routes above are public; no reference-data write routes are
exposed. Omit Authorization on anonymous requests, since invalid or expired
bearer tokens are still rejected.

**Technical debt:** Locations, sub-locations and villages are not yet modelled,
seeded, or exposed in an application form. The intended interim behaviour is
validated open text for location and village rather than inventing a list. A future
implementation must obtain and reconcile an authoritative county → sub-county →
location → sub-location → village dataset, migrate free-text values through a
reviewed matching process, add integer foreign keys and cascading selection, and
protect historical records. This is a deliberate deviation from the assessment's
full geography requirement, not a completed feature.
