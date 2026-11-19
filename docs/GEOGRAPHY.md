# County and sub-county reference data

The provisional master is `src/main/resources/geography.yml`: 47 counties and 333
sub-counties, with the supplied 2009 and 2019 census populations. It uses the
user-supplied [census table](https://www.citypopulation.de/en/kenya/sub/admin/)
for names, county associations and counts. County codes are cross-checked against
the [provided gist](https://gist.github.com/Aroniez/a6b4e380bc7b9abdf595470749fb68cc);
all 47 county names/codes match after normalising `Trans-Nzoia` to `Trans Nzoia`.
The gist is not an authoritative gazette and appears to mix constituencies with
sub-counties. Its Kisii list is empty, while the census list has 11 sub-counties.

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

Flyway creates integer-keyed `counties` and `sub_counties` tables. On every startup,
the application validates the complete YAML and applies the reference seed in one
transaction. County IDs are the supplied codes; sub-county IDs are generated and
stable across restarts. Existing seed rows are updated by county ID/name and
county/name respectively; extra database rows are not deleted. A county ID/name
conflict or invalid seed stops startup. Renaming a sub-county in the YAML creates a
new row rather than silently reassigning an existing identity. Such changes require
an explicit reviewed migration and reference reconciliation. Concurrent startup
can conflict/deadlock: deploy one migration/seeding instance at a time.

The seed writes reference data through JDBC intentionally: startup has no
authenticated user, while JPA `Base` auditing rejects unauthenticated writes.
These tables are lookup data, not JPA entities, and have no misleading user audit
actor. Do not expose direct reference-table writes to normal users.

Authenticated cascading lookup endpoints:

- `GET /api/geography/counties` returns `id` and `name`.
- `GET /api/geography/counties/{countyId}/sub-counties` returns `id`, `countyId`
  and `name`; an unknown county returns 404.

Results are alphabetically sorted and use the standard response wrapper. Login
and user provisioning are not implemented yet, so tokens must currently be
provisioned through a controlled development method.

**Technical debt:** Locations, sub-locations and villages are not yet modelled,
seeded, or exposed in an application form. The intended interim behaviour is
validated open text for location and village rather than inventing a list. A future
implementation must obtain and reconcile an authoritative county → sub-county →
location → sub-location → village dataset, migrate free-text values through a
reviewed matching process, add integer foreign keys and cascading selection, and
protect historical records. This is a deliberate deviation from the assessment's
full geography requirement, not a completed feature.
