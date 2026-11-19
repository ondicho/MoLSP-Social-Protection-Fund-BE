package MoLSP.go.ke.Social_Assistance_Fund.services;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.ApplicationDtos;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.PaginatedResponse;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.ResponseWrapper;
import MoLSP.go.ke.Social_Assistance_Fund.enums.ApplicationStatus;
import MoLSP.go.ke.Social_Assistance_Fund.enums.Role;
import MoLSP.go.ke.Social_Assistance_Fund.exceptions.DuplicateResourceException;
import MoLSP.go.ke.Social_Assistance_Fund.exceptions.InvalidStateTransitionException;
import MoLSP.go.ke.Social_Assistance_Fund.exceptions.ResourceNotFoundException;
import MoLSP.go.ke.Social_Assistance_Fund.models.Applicant;
import MoLSP.go.ke.Social_Assistance_Fund.models.ApplicationTransition;
import MoLSP.go.ke.Social_Assistance_Fund.models.SocialAssistanceApplication;
import MoLSP.go.ke.Social_Assistance_Fund.models.SubCounty;
import MoLSP.go.ke.Social_Assistance_Fund.models.User;
import MoLSP.go.ke.Social_Assistance_Fund.repositories.ApplicantRepository;
import MoLSP.go.ke.Social_Assistance_Fund.repositories.ApplicationTransitionRepository;
import MoLSP.go.ke.Social_Assistance_Fund.repositories.SocialAssistanceApplicationRepository;
import MoLSP.go.ke.Social_Assistance_Fund.utils.PaginationUtil;
import MoLSP.go.ke.Social_Assistance_Fund.utils.PhoneNumberNormalizer;
import MoLSP.go.ke.Social_Assistance_Fund.utils.PublicRegistrationAuditContext;
import MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final ApplicantRepository applicants;
    private final SocialAssistanceApplicationRepository applications;
    private final ApplicationTransitionRepository transitions;
    private final JdbcTemplate jdbc;
    private final PaginationUtil pagination;
    private final ResponseHandler responses;
    private final PhoneNumberNormalizer phoneNumbers;
    private final org.springframework.data.domain.AuditorAware<String> auditor;
    private final EntityManager entityManager;

    @Transactional
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> createAssisted(
            ApplicationDtos.FormRequest request, String requestId) {
        String actor = currentActor();
        validatePartial(request);
        Applicant applicant = createApplicant(request);
        SocialAssistanceApplication application = newApplication(applicant, request, "ASSISTED",
                ApplicationStatus.DRAFT);
        applications.save(application);
        entityManager.flush();
        recordTransition(application, null, ApplicationStatus.DRAFT, null, actor);
        return responses.created(toResponse(application), "Application draft created", requestId);
    }

    @Transactional
    public ResponseEntity<ResponseWrapper<ApplicationDtos.RegistrationReceipt>> selfRegister(
            ApplicationDtos.FormRequest request, String requestId) {
        validateComplete(request);
        ApplicationDtos.RegistrationReceipt receipt = PublicRegistrationAuditContext.execute(() -> {
            Applicant applicant = createApplicant(request);
            SocialAssistanceApplication application = newApplication(applicant, request, "SELF_SERVICE",
                    ApplicationStatus.SUBMITTED);
            applications.save(application);
            entityManager.flush();
            recordTransition(application, ApplicationStatus.DRAFT, ApplicationStatus.SUBMITTED,
                    null, PublicRegistrationAuditContext.ACTOR);
            entityManager.flush();
            return new ApplicationDtos.RegistrationReceipt(application.getId(), application.getStatus());
        });
        return responses.created(receipt, "Application submitted", requestId);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ResponseWrapper<PaginatedResponse<ApplicationDtos.ApplicationResponse>>> list(
            Integer pageNo, Integer pageSize, String sortBy, String sortDirection, String search,
            ApplicationStatus status, LocalDate from, LocalDate to, String programme,
            Integer countyId, Integer subCountyId, String location, String subLocation, String village,
            String requestId) {
        if (sortBy != null && !sortBy.isBlank()
                && !Set.of("createdAt", "updatedAt", "applicationDate", "status").contains(sortBy)) {
            throw new IllegalArgumentException("Unsupported application sort field");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("applicationDateFrom must not be after applicationDateTo");
        }
        User actor = currentUser();
        Pageable pageable = pagination.buildPageable(pageNo, pageSize, sortBy, sortDirection, "createdAt");
        Page<ApplicationDtos.ApplicationResponse> page =
                applications.findAll(specification(search, status, from, to, programme,
                                countyId, subCountyId, location, subLocation, village, actor), pageable)
                        .map(this::toResponse);
        return responses.paginated(page, "Applications retrieved", requestId);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> get(UUID id, String requestId) {
        SocialAssistanceApplication application = find(id);
        ensureCanView(application);
        return responses.success(toResponse(application), "Application retrieved", requestId);
    }

    @Transactional
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> updateDraft(
            UUID id, ApplicationDtos.UpdateRequest request, String requestId) {
        SocialAssistanceApplication application = find(id);
        ensureOwner(application);
        ensureVersion(application, request.version());
        requireStatus(application, ApplicationStatus.DRAFT, "Only draft applications can be edited");
        validatePartial(request.form());
        String actor = currentActor();
        ApplicationStatus current = application.getStatus();
        applyApplicant(application.getApplicant(), request.form());
        applyApplication(application, request.form());
        applications.save(application);
        recordTransition(application, current, current, "Draft updated", actor);
        entityManager.flush();
        return responses.success(toResponse(application), "Application draft updated", requestId);
    }

    @Transactional
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> submit(
            UUID id, ApplicationDtos.ActionRequest request, String requestId) {
        SocialAssistanceApplication application = find(id);
        ensureOwner(application);
        ensureVersion(application, request.version());
        requireStatus(application, ApplicationStatus.DRAFT, "Only draft applications can be submitted");
        validateComplete(toFormRequest(application));
        return changeStatus(application, ApplicationStatus.SUBMITTED, null, "Application submitted", requestId);
    }

    @Transactional
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> startReview(
            UUID id, ApplicationDtos.ActionRequest request, String requestId) {
        SocialAssistanceApplication application = find(id);
        ensureVersion(application, request.version());
        requireStatus(application, ApplicationStatus.SUBMITTED, "Only submitted applications can enter review");
        return changeStatus(application, ApplicationStatus.UNDER_REVIEW, null,
                "Application review started", requestId);
    }

    @Transactional
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> approve(
            UUID id, ApplicationDtos.ApprovalRequest request, String requestId) {
        SocialAssistanceApplication application = find(id);
        ensureVersion(application, request.version());
        requireStatus(application, ApplicationStatus.UNDER_REVIEW,
                "Only applications under review can be approved");
        validateOfficialDate(application, request.officialSignedDate());
        application.setOfficialName(request.officialName().strip());
        application.setOfficialDesignation(request.officialDesignation().strip());
        application.setOfficialSignatureReference(request.officialSignatureReference().strip());
        application.setOfficialSignedDate(request.officialSignedDate());
        return changeStatus(application, ApplicationStatus.APPROVED, null, "Application approved", requestId);
    }

    @Transactional
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> reject(
            UUID id, ApplicationDtos.DecisionRequest request, String requestId) {
        SocialAssistanceApplication application = find(id);
        ensureVersion(application, request.version());
        requireStatus(application, ApplicationStatus.UNDER_REVIEW,
                "Only applications under review can be rejected");
        return changeStatus(application, ApplicationStatus.REJECTED, request.reason().strip(),
                "Application rejected", requestId);
    }

    @Transactional
    public ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> returnForCorrection(
            UUID id, ApplicationDtos.DecisionRequest request, String requestId) {
        SocialAssistanceApplication application = find(id);
        ensureVersion(application, request.version());
        requireStatus(application, ApplicationStatus.UNDER_REVIEW,
                "Only applications under review can be returned for correction");
        return changeStatus(application, ApplicationStatus.RETURNED_FOR_CORRECTION, request.reason().strip(),
                "Application returned for correction", requestId);
    }

    private Specification<SocialAssistanceApplication> specification(
            String search, ApplicationStatus status, LocalDate from, LocalDate to, String programme,
            Integer countyId, Integer subCountyId, String location, String subLocation, String village,
            User actor) {
        Specification<SocialAssistanceApplication> spec = (root, query, cb) -> cb.conjunction();
        if (actor.getRole() == Role.DATA_ENTRY_OFFICER) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("createdBy"), actor.getId().toString()));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.strip().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> {
                var applicant = root.join("applicant");
                return cb.or(cb.like(cb.lower(applicant.get("firstName")), pattern),
                        cb.like(cb.lower(applicant.get("lastName")), pattern),
                        cb.like(cb.lower(applicant.get("identityNumber")), pattern));
            });
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("applicationDate"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("applicationDate"), to));
        }
        if (programme != null && !programme.isBlank()) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                return cb.equal(root.join("programmeCodes"), programme.strip().toUpperCase(Locale.ROOT));
            });
        }
        if (countyId != null || subCountyId != null || hasText(location) || hasText(subLocation) || hasText(village)) {
            spec = spec.and((root, query, cb) -> {
                var applicant = root.join("applicant");
                var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
                if (countyId != null) {
                    predicates.add(cb.equal(applicant.get("subCounty").get("county").get("id"), countyId));
                }
                if (subCountyId != null) predicates.add(cb.equal(applicant.get("subCounty").get("id"), subCountyId));
                if (hasText(location)) predicates.add(cb.equal(cb.lower(applicant.get("location")), location.strip().toLowerCase(Locale.ROOT)));
                if (hasText(subLocation)) predicates.add(cb.equal(cb.lower(applicant.get("subLocation")), subLocation.strip().toLowerCase(Locale.ROOT)));
                if (hasText(village)) predicates.add(cb.equal(cb.lower(applicant.get("village")), village.strip().toLowerCase(Locale.ROOT)));
                return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
            });
        }
        return spec;
    }

    private Applicant createApplicant(ApplicationDtos.FormRequest request) {
        String identity = identityNumber(request.identityNumber());
        if (identity != null && applicants.findByIdentityNumber(identity).isPresent()) {
            throw new DuplicateResourceException("An applicant with this identity number already exists");
        }
        Applicant applicant = new Applicant();
        applyApplicant(applicant, request);
        return applicants.save(applicant);
    }

    private void applyApplicant(Applicant applicant, ApplicationDtos.FormRequest request) {
        applicant.setFirstName(clean(request.firstName()));
        applicant.setMiddleName(clean(request.middleName()));
        applicant.setLastName(clean(request.lastName()));
        applicant.setGender(request.gender());
        applicant.setDateOfBirth(request.dateOfBirth());
        applicant.setAge(request.age());
        applicant.setAge(applicant.calculateAge());
        applicant.setMaritalStatusCode(normalizeCode(request.maritalStatus()));
        applicant.setIdentityNumber(identityNumber(request.identityNumber()));
        applicant.setSubCounty(request.subCountyId() == null ? null
                : entityManager.getReference(SubCounty.class, request.subCountyId()));
        applicant.setLocation(clean(request.location()));
        applicant.setSubLocation(clean(request.subLocation()));
        applicant.setVillage(clean(request.village()));
        applicant.setPostalAddress(clean(request.postalAddress()));
        applicant.setPhysicalAddress(clean(request.physicalAddress()));
        validateReferences(request.subCountyId());
        Set<String> normalizedContacts = request.telephoneContacts() == null ? Set.of()
                : request.telephoneContacts().stream().map(phoneNumbers::normalize)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        applicant.getTelephoneContacts().clear();
        applicant.getTelephoneContacts().addAll(normalizedContacts);
    }

    private SocialAssistanceApplication newApplication(Applicant applicant, ApplicationDtos.FormRequest request,
                                                        String source, ApplicationStatus status) {
        SocialAssistanceApplication application = new SocialAssistanceApplication();
        application.setApplicant(applicant);
        application.setSource(source);
        application.setStatus(status);
        applyApplication(application, request);
        return application;
    }

    private void applyApplication(SocialAssistanceApplication application, ApplicationDtos.FormRequest request) {
        application.setApplicationDate(request.applicationDate());
        application.setApplicantSignedDate(request.applicantSignedDate());
        Set<String> codes = request.programmeCodes() == null ? Set.of()
                : request.programmeCodes().stream().map(this::normalizeCode)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        validateProgrammes(codes);
        application.getProgrammeCodes().clear();
        application.getProgrammeCodes().addAll(codes);
        application.setOtherProgramme(clean(request.otherProgramme()));
        validateDateConsistency(application.getApplicationDate(), application.getApplicantSignedDate());
    }

    private void validatePartial(ApplicationDtos.FormRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Application form is required");
        }
        if (request.age() != null && (request.age() < 0 || request.age() > 120)) {
            throw new IllegalArgumentException("age must be between 0 and 120");
        }
        if (request.dateOfBirth() != null && request.dateOfBirth().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("dateOfBirth must not be in the future");
        }
        if (request.subCountyId() != null) {
            validateReferences(request.subCountyId());
        }
        validateOptionalCode("marital_statuses", request.maritalStatus());
        if (request.programmeCodes() != null) {
            validateProgrammes(request.programmeCodes().stream().map(this::normalizeCode)
                    .collect(java.util.stream.Collectors.toSet()));
        }
        validateDateConsistency(request.applicationDate(), request.applicantSignedDate());
    }

    private void validateComplete(ApplicationDtos.FormRequest request) {
        validatePartial(request);
        Applicant applicant = new Applicant();
        applyApplicant(applicant, request);
        List<String> missing = new ArrayList<>();
        if (!hasText(request.firstName())) missing.add("firstName");
        if (!hasText(request.lastName())) missing.add("lastName");
        if (request.gender() == null) missing.add("gender");
        if (request.dateOfBirth() == null && request.age() == null) missing.add("dateOfBirth or age");
        if (!hasText(request.maritalStatus())) missing.add("maritalStatus");
        if (identityNumber(request.identityNumber()) == null) missing.add("identityNumber");
        if (request.subCountyId() == null) missing.add("subCountyId");
        if (!hasText(request.location())) missing.add("location");
        if (!hasText(request.subLocation())) missing.add("subLocation");
        if (!hasText(request.village())) missing.add("village");
        if (!hasText(request.postalAddress())) missing.add("postalAddress");
        if (!hasText(request.physicalAddress())) missing.add("physicalAddress");
        if (request.telephoneContacts() == null || request.telephoneContacts().isEmpty()) missing.add("telephoneContacts");
        if (request.applicationDate() == null) missing.add("applicationDate");
        if (request.applicantSignedDate() == null) missing.add("applicantSignedDate");
        if (request.programmeCodes() == null || request.programmeCodes().isEmpty()) missing.add("programmeCodes");
        boolean hasOther = request.programmeCodes() != null && request.programmeCodes().stream()
                .anyMatch(code -> "OTHER".equalsIgnoreCase(code.strip()));
        if (hasOther && !hasText(request.otherProgramme())) missing.add("otherProgramme");
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Required application fields are missing: " + String.join(", ", missing));
        }
    }

    private void validateReferences(Integer subCountyId) {
        if (subCountyId != null) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sub_counties WHERE id = ?",
                    Integer.class, subCountyId);
            if (count == null || count == 0) {
                throw new ResourceNotFoundException("Sub-county", subCountyId);
            }
        }
    }

    private void validateProgrammes(Set<String> codes) {
        if (codes.isEmpty()) return;
        String placeholders = String.join(",", java.util.Collections.nCopies(codes.size(), "?"));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM assistance_programmes WHERE code IN ("
                + placeholders + ")", Integer.class, codes.toArray());
        if (count == null || count != codes.size()) {
            throw new IllegalArgumentException("One or more assistance programme codes are invalid");
        }
    }

    private void validateOptionalCode(String table, String code) {
        if (!hasText(code)) return;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE code = ?",
                Integer.class, normalizeCode(code));
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Invalid maritalStatus");
        }
    }

    private void validateDateConsistency(LocalDate applicationDate, LocalDate applicantSignedDate) {
        if (applicationDate != null && applicantSignedDate != null && applicantSignedDate.isBefore(applicationDate)) {
            throw new IllegalArgumentException("applicantSignedDate cannot precede applicationDate");
        }
    }

    private void validateOfficialDate(SocialAssistanceApplication application, LocalDate officialSignedDate) {
        if (application.getApplicationDate() == null || application.getApplicantSignedDate() == null
                || officialSignedDate.isBefore(application.getApplicationDate())
                || officialSignedDate.isBefore(application.getApplicantSignedDate())) {
            throw new IllegalArgumentException("officialSignedDate cannot precede application or applicant signed date");
        }
    }

    private void recordTransition(SocialAssistanceApplication application, ApplicationStatus previous,
                                  ApplicationStatus next, String reason, String actor) {
        ApplicationTransition transition = new ApplicationTransition();
        transition.setApplication(application);
        transition.setActorId(actor);
        transition.setOccurredAt(Instant.now());
        transition.setPreviousStatus(previous);
        transition.setNewStatus(next);
        transition.setReason(reason);
        transitions.save(transition);
    }

    private ResponseEntity<ResponseWrapper<ApplicationDtos.ApplicationResponse>> changeStatus(
            SocialAssistanceApplication application, ApplicationStatus next, String reason,
            String message, String requestId) {
        ApplicationStatus previous = application.getStatus();
        String actor = currentActor();
        application.setStatus(next);
        application.setDecisionReason(reason);
        applications.save(application);
        recordTransition(application, previous, next, reason, actor);
        entityManager.flush();
        return responses.success(toResponse(application), message, requestId);
    }

    private void ensureOwner(SocialAssistanceApplication application) {
        User actor = currentUser();
        if (actor.getRole() != Role.DATA_ENTRY_OFFICER
                || !application.getCreatedBy().equals(actor.getId().toString())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only the creating Data Entry Officer may edit this application");
        }
    }

    private void ensureCanView(SocialAssistanceApplication application) {
        User actor = currentUser();
        if (actor.getRole() == Role.DATA_ENTRY_OFFICER
                && !application.getCreatedBy().equals(actor.getId().toString())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Data Entry Officers may only view their own applications");
        }
    }

    private void ensureVersion(SocialAssistanceApplication application, long expectedVersion) {
        if (application.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(SocialAssistanceApplication.class, application.getId());
        }
    }

    private void requireStatus(SocialAssistanceApplication application, ApplicationStatus expected, String message) {
        if (application.getStatus() != expected) {
            throw new InvalidStateTransitionException(message);
        }
    }

    private SocialAssistanceApplication find(UUID id) {
        return applications.findById(id).orElseThrow(() -> new ResourceNotFoundException("Application", id));
    }

    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof User user) || user.getId() == null || !user.isEnabled()) {
            throw new org.springframework.security.access.AccessDeniedException("An authenticated user is required");
        }
        return user;
    }

    private String currentActor() {
        return auditor.getCurrentAuditor().orElseThrow();
    }

    private ApplicationDtos.ApplicationResponse toResponse(SocialAssistanceApplication application) {
        Applicant applicant = application.getApplicant();
        return new ApplicationDtos.ApplicationResponse(application.getId(), application.getStatus(),
                application.getSource(), applicant.getFirstName(), applicant.getMiddleName(), applicant.getLastName(),
                applicant.getGender(), applicant.getDateOfBirth(), applicant.calculateAge(), applicant.getMaritalStatusCode(),
                applicant.getIdentityNumber(), applicant.getSubCounty() == null
                        || applicant.getSubCounty().getCounty() == null ? null : applicant.getSubCounty().getCounty().getId(),
                applicant.getSubCounty() == null ? null : applicant.getSubCounty().getId(), applicant.getLocation(),
                applicant.getSubLocation(), applicant.getVillage(), applicant.getPostalAddress(), applicant.getPhysicalAddress(),
                applicant.getTelephoneContacts().stream().sorted().toList(), application.getApplicationDate(),
                application.getProgrammeCodes().stream().sorted().toList(), application.getOtherProgramme(),
                application.getApplicantSignedDate(), application.getOfficialName(), application.getOfficialDesignation(),
                application.getOfficialSignatureReference(), application.getOfficialSignedDate(),
                application.getDecisionReason(), application.getVersion(), application.getCreatedAt(), application.getUpdatedAt());
    }

    private ApplicationDtos.FormRequest toFormRequest(SocialAssistanceApplication application) {
        Applicant applicant = application.getApplicant();
        return new ApplicationDtos.FormRequest(applicant.getFirstName(), applicant.getMiddleName(), applicant.getLastName(),
                applicant.getGender(), applicant.getDateOfBirth(), applicant.calculateAge(), applicant.getMaritalStatusCode(),
                applicant.getIdentityNumber(), applicant.getSubCounty() == null ? null : applicant.getSubCounty().getId(),
                applicant.getLocation(),
                applicant.getSubLocation(), applicant.getVillage(), applicant.getPostalAddress(), applicant.getPhysicalAddress(),
                new ArrayList<>(applicant.getTelephoneContacts()), application.getApplicationDate(),
                new ArrayList<>(application.getProgrammeCodes()), application.getOtherProgramme(),
                application.getApplicantSignedDate());
    }

    private String identityNumber(String value) {
        return hasText(value) ? value.strip().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeCode(String value) {
        return hasText(value) ? value.strip().toUpperCase(Locale.ROOT) : null;
    }

    private String clean(String value) {
        return hasText(value) ? value.strip() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
