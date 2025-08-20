# AI DEVELOPMENT GUIDELINES - CRITICAL INSTRUCTIONS

## ⚠️ MANDATORY READ BEFORE ANY CODE CHANGES ⚠️

### RULE #1: DO NOT MODIFY COMPREHENSIVE EXISTING FILES
- **If a file looks comprehensive, DO NOT change it**
- **USE the existing file as-is**
- **READ and UNDERSTAND how it works**
- **FIGURE OUT why it was designed that way**
- **WORK WITH the existing architecture, not against it**

### RULE #2: RESPECT EXISTING ARCHITECTURE
- Files that are overlooked initially but found later are NOT to be touched
- Existing comprehensive implementations should be preserved
- Study the existing code patterns and follow them
- Do not create duplicate functionality when it already exists

### RULE #3: INVESTIGATION BEFORE MODIFICATION
Before making ANY changes:
1. **THOROUGHLY explore the entire codebase**
2. **IDENTIFY all existing implementations**
3. **UNDERSTAND the current architecture**
4. **WORK WITH existing patterns**
5. **Only modify when absolutely necessary**

### RULE #4: EXAMPLES OF WHAT NOT TO DO
- ❌ Creating new files when comprehensive ones already exist
- ❌ Replacing working implementations with "simpler" versions
- ❌ Modifying complex files without understanding their purpose
- ❌ Duplicating functionality that already exists

### RULE #5: EXAMPLES OF WHAT TO DO
- ✅ Use existing comprehensive files as-is
- ✅ Study and understand existing implementations
- ✅ Work within the established architecture
- ✅ Make minimal, targeted fixes only when necessary
- ✅ Preserve existing functionality while fixing specific issues

### RECENT EXAMPLE:
The PredictionOverlayView class already existed within PredictionOverlayService.kt as a comprehensive implementation. Instead of creating a duplicate file, the existing implementation was enhanced with minimal changes to add debugging features while preserving all existing functionality.

## THIS FILE MUST BE READ AND ACKNOWLEDGED BEFORE ANY DEVELOPMENT WORK

**Failure to follow these guidelines may result in breaking working functionality and creating unnecessary complexity.**