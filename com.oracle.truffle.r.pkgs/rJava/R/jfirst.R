## This file is part of the rJava package - low-level R/Java interface
## (C)2006 Simon Urbanek <simon.urbanek@r-project.org>
## (C)2021, Oracle and/or its affiliates.
## For license terms see DESCRIPTION and/or LICENSE
##
## $Id$

# this part is common to all platforms and must be invoked
# from .First.lib after library.dynam

# actual namespace environment of this package
.env <- environment()

# variables in the rJava environment that will be initialized *after* the package is loaded
# they need to be pre-created at load time and populated later by .jinit
.delayed.export.variables <- c(".jniInitialized", ".jclassObject", ".jclassString", ".jclassClass",
                               ".jclass.int", ".jclass.double", ".jclass.float", ".jclass.boolean",
                               ".jclass.void", ".jinit.merge.error")
# variables that are delayed but not exported are added here
.delayed.variables <- c(.delayed.export.variables, ".rJava.class.loader")

.jfirst <- function(libname, pkgname) {
  # FASTR - no more C entry points
  .registerFastrFunctions()
  # register all C entry points
  # addr <- getNativeSymbolInfo(.register.addr, pkgname)
  # for (name in .register.addr)
  #    .env[[name]] <- addr[[name]]$address
  
  # previously set in C
  assign(".rJava_initialized", FALSE, .env)  
  
  assign(".rJava.base.path", paste(libname, pkgname, sep=.Platform$file.sep), .env)
  assign(".jzeroRef", .createZeroRef(), .env)

  for (x in .delayed.variables) assign(x, NULL, .env)
  assign(".jniInitialized", FALSE, .env)

  # FASTR - no JVM params
  # # default JVM initialization parameters
  # if (is.null(getOption("java.parameters")))
  #   options("java.parameters"="-Xmx512m")

  # assign(".rJava.debug", T, .env)  

  ## S4 classes update - all classes are created earlier in classes.R, but jobjRef's prototype is only valid after the dylib is loaded
  setClass("jobjRef", representation(jobj="externalptr", jclass="character"), prototype=list(jobj=.jzeroRef, jclass="java/lang/Object"), where=.env, validity=.jobjRef.validity)
}

# FASTR <<<<<

.registerFastrFunctions <- function() {
  # .C
  .fastr.register.functions("rJava", .env, 0, 
        list(RJavaCheckExceptions = .RJavaCheckExceptions,
             RclearException=.RclearException))
  # .External  
  .fastr.register.functions("rJava", .env, 3, 
        list(RcreateObject = .RcreateObject, 
             RgetStringValue = .RgetStringValue, 
             RinitJVM = .RinitJVM, 
             RtoString = .RtoString, 
             RcallMethod = .RcallMethod))
  # .Call
  .fastr.register.functions("rJava", .env, 1, 
        list(RpollException = .RpollException,
            RthrowException = .RthrowException,                                               
            RidenticalRef = .RidenticalRef,
            RisAssignableFrom = .RisAssignableFrom,
            RJava_checkJVM = .RJava_checkJVM,
            RJava_needs_init = .RJava_needs_init,
            initRJavaTools = .initRJavaTools,            
            RJava_set_memprof = .RJava_set_memprof,
            RgetStringArrayCont = .RgetStringArrayCont,
            RgetIntArrayCont = .RgetIntArrayCont,                 
            RgetBoolArrayCont = .RgetBoolArrayCont,
            RgetCharArrayCont = .RgetCharArrayCont,
            RgetShortArrayCont = .RgetShortArrayCont,
            RgetByteArrayCont = .RgetByteArrayCont,
            RgetDoubleArrayCont = .RgetDoubleArrayCont,
            RgetFloatArrayCont = .RgetFloatArrayCont,
            RgetLongArrayCont = .RgetLongArrayCont,
            RgetObjectArrayCont = .RgetObjectArrayCont,
            RcreateArray = .RcreateArray,
            RgetField = .RgetField,
            RsetField = .RsetField,
            RgetSimpleClassNames = .RgetSimpleClassNames))
}

.createZeroRef <- function() {
    zr <- methods:::.newExternalptr()    
    attr(zr, ".rJava.zeroRef") <- T
    zr
}

.jobjRef.validity <- function(object) { 
    object # force args

    o <- attr(object@jobj, "external.object", exact=TRUE)
    if(is.null(o)) {
        zr <- attr(object@jobj, ".rJava.zeroRef", exact=TRUE)
        if(is.null(zr)) {
            return(FALSE)
        }
    } else if(!is.polyglot.value(o)) {
        # truffle unboxes first and fastr then converts primitive types into equivalent R values
        # and the external.object attr in such a case isn't a truffle object 
        # in e.g. .jnew and .jcall rJava knows and stores the return value class name in the jclass slot,
        # but it is only the externalptr, that is passed into functions like RcallMethod 
        # and we might need the class name for calls like getClass or isInstance
        attr(object@jobj, "external.classname") <- object@jclass
    }
    return(TRUE)
}

# TODO issues:
# - bytes converted to int
# - chars converted to int


.RJavaCheckExceptions <- function(silent) {
    silent # force args
    res <- .fastr.interop.checkException(silent, ".jcheck")
    if(is.list(res)) {
        if(!silent) {            
            ex <- structure(
                list(
                  message = res$msg,
                  call = res$call,
                  jobj = new("jobjRef", jobj=.fromJ(res$jobj, toExtPointer=TRUE), jclass=res$jobj$getClass()$getName())
                ),
                class = .RgetSimpleClassNames(res$jobj, TRUE)
            )
            stop(ex)
        }
        return(1)
    } else {
        return(res)
    }
}

.RpollException <- function() {    
    e <- .fastr.interop.getTryException(FALSE)
    if(is.null(e)) {
        return(NULL)
    }
    .fromJ(e)
}

.RclearException <- function() {    
    .fastr.interop.clearTryException()
}

.RthrowException <- function(silent) {    
    # TODO do we need this? not used directly from rJava code
}

.RidenticalRef <- function(x1, x2) {
    x1; x2 # force args

    if(!inherits(x1, "externalptr") || !inherits(x2, "externalptr")) {
        return(NULL)
    }
    o1 <- attr(x1, "external.object", exact=TRUE)
    o2 <- attr(x2, "external.object", exact=TRUE)
    if(!(is.polyglot.value(o1) && is.polyglot.value(o2))) {
        return(identical(o1, o2))
    }
    if(!is.polyglot.value(o1) || !is.polyglot.value(o2)) {
        return(NULL)
    }
    .fastr.interop.isIdentical(o1, o2)
}

.RisAssignableFrom <- function(cl1, cl2) {
    cl1; cl2 # force args

    if(!inherits(cl1, "externalptr") || !inherits(cl2, "externalptr")) {
        stop("invalid type")
    }
    # should be already ensured that both args are class extpointer-s
    .fastr.interop.isAssignableFrom(attr(cl1, "external.object", exact=TRUE), attr(cl2, "external.object", exact=TRUE))  
}

.RcallMethod <- function(obj, returnSig, method, ...) {
    obj; returnSig; method # force args

    if(is.null(obj)) {
        stop("RcallMethod: call on a NULL object")
    }

    o <- NULL
    clnam <- NULL
    if(inherits(obj, "externalptr")) {
        o <- attr(obj, "external.object", exact=TRUE)
    } else if(is.character(obj) && length(obj) == 1) {
        clnam <- obj
    } else {
        stop("RcallMethod: invalid object parameter")
    }
    if(is.null(o) && is.null(clnam)) {
        stop("RcallMethod: attempt to call a method of a NULL object.")
    }
  
    # <<<<<< j.l.Class HACKs <<<<<<
    # truffle provides no access to j.l.Class methods
    if (method == "forName") {
        if (!is.null(clnam) && clnam %in% c("java/lang/Class", "java.lang.Class")) {
            res <- .fastr.interop.try(function() { 
                jt <- java.type(list(...)[[1]]) 
                jt$class
            }, FALSE)         
            return(.fromJ(res))
        }
    } else if (method == "getClass") {
        if(is.polyglot.value(o)) {            
            extClName <- attr(obj, "external.classname", exact=TRUE)
            if(!is.null(extClName)) {
                res <- java.type(extClName)
                res <- res$class
                return(.fromJ(res))
            }                        
        }
    } 
    # >>>>>> j.l.Class HACKs >>>>>>
    
    if (!is.null(o) && !is.polyglot.value(o)) {        
        o <- .asTruffleObject(o, attr(obj, "external.classname", exact=TRUE))
    } 

    if(!is.character(returnSig) || length(returnSig) != 1) {
        stop("RcallMethod: invalid return signature parameter")
    }

    if(!is.character(method) || length(method) != 1) {
        stop("RcallMethod: invalid method name")
    }

    # if((!is.null(o) && !(method %in% names(o))) ||        
    #    (!is.null(cls) && !(method %in% names(cls))) ) {
    #     stop(paste0("method ", method, " with signature ", returnSig, " not found"))
    # }

    if(is.null(o)) { 
        cls <- NULL
        if (!is.null(clnam)) {
            cls <- .fastr.interop.try(function() { java.type(clnam) }, FALSE)
        } 
        if (is.null(cls)) {
            stop("RcallMethod: cannot determine object class")
        }

        extMethod <- function(...) cls[method](...)
    } else {
        extMethod <- function(...) o[method](...)
    }

    args <- .ellipsisToJ(...)
    res <- .fastr.interop.try(function() { do.call(extMethod, args) }, FALSE)
    if(is.null(res)) {
        return(NULL)
    }

    if(substr(returnSig, 1, 1) %in% c("L", "[")) {        

        # >>>>>> char conversion HACK >>>>>>
        # TODO not sure if this is necessary at the current state of things in rJava
        # - .jsimplify doesnt work for Characer S4 jrefobj and there is no other way 
        #   how to get the primitive out of it
        # - might be it would be better to patch .jsimplify to call .charToInt(res) 
        #   instead of calling .intValue() on a Character (pointer)
        # if(returnSig == "Ljava/lang/Character;") {
        #     # char -> integer
        #     res <- .charToInt(res)
        #     ep <- .fromJ(res, toExtPointer=TRUE)
        #     attr(ep, "external.classname") <- 'java.lang.Character;'
        #     return(ep)
        #  }
        # <<<<<< char conversion HACK <<<<<<

        .fromJ(res, toExtPointer=TRUE)
    } else {
        # <<<<<< char conversion HACK <<<<<<
        if(returnSig == "C") {
            res <- .charToInt(res)
        }
        # >>>>>> char conversion HACK >>>>>>
        .fromJ(res, toExtPointer=FALSE)
    }
}

.RgetField <- function(obj, sig, name, trueclass) {
    obj; sig; name; trueclass # force args

    if(is.null(obj)) {
        return(NULL)
    }

    if (!is.character(name) || length(name) != 1) {
        stop("invalid field name")
    }

    if (!is.null(sig) && (!is.character(sig) || length(sig) != 1)) {
      stop("invalid signature parameter")
    }
    
    if(.IS_JOBJREF(obj)) {
        obj <- obj@jobj
    }
    clnam <- NULL
    cls <- NULL
    externalClassName <- NULL
    o <- NULL
    if(inherits(obj, "externalptr")) {
        o <- .extPointerToJ(obj)
        externalClassName <- attr(obj, "external.classname", exact=TRUE)
    } else if(is.character(obj) && length(obj) == 1) {
        clnam <- obj
    } else {
        stop("invalid object parameter")
    }
    if (is.null(o) && is.null(clnam)) {
        stop("cannot access a field of a NULL object")
    }
        
    if(!is.null(o)) {
        if(!is.polyglot.value(o)) {
            o <- .asTruffleObject(o, externalClassName)            
        }
        res <- o[name]
    } else {
        cls <- java.type(clnam)
        if (is.null(cls)) {
            stop("cannot determine object class")
        }
        res <- cls[name]
    }    
    if(is.null(res)) {
        return(.jnull())
    }

    # if field is an Object then, (differently than in RcallMethod), 
    # we have to return a S4 objects at this place
    if(!is.polyglot.value(res)) {
        # TODO there are cases when the passed signature is NULL - e.g. rJavaObject$someField 
        # with truffle we have no way to differentiate if the unboxed return value relates to an Object or primitive field
        # but the original field.c RgetField implementation checks the return type and 
        # if field not primitive an "jobjRef" S4 object is returned. Fortunately, there is RJavaTools.getFieldType()
        if(trueclass) {
            if(!is.null(cls)) {
                clsname <- .getFieldTypeName(cls, name)
            } else {
                clsname <- .getFieldTypeName(o$getClass(), name)
            }
            if(!startsWith(clsname, "java.lang")) {
                # not polyglot object and not java.lang - must be primitive                

                if(clsname == "char") {
                    # char conversion HACK
                    res <- .charToInt(res)
                }

                return(res)
            }
            clsname <- gsub(".", "/", clsname, fixed=T)
        } else {
            if(is.null(sig)) {
                if(is.null(cls)) {
                    sig <- .getFieldTypeName(o$getClass(), name)
                } else {                
                    sig <- .getFieldTypeName(cls, name)
                }
            }
            if(!startsWith(sig, "L") && !startsWith(sig, "java.lang")) {
                # not polyglot object and not java.lang - must be primitive

                if(sig == "char") {
                    # char conversion HACK
                    res <- .charToInt(res)
                }

                return(res)
            }
            clsname <- gsub(".", "/", .signatureToClassName(sig), fixed=T)
        }
        res <- .asTruffleObject(res)
    } else {
        if(trueclass) {
            clsname <- gsub(".", "/", res$getClass()$getName(), fixed=T)
        } else {
            if(is.null(sig)) {
                # should not happed
                sig <- gsub(".", "/", res$getClass()$getName(), fixed=T)
            }            
            clsname <- .signatureToClassName(sig)
        }
    }
    
    res <- .fromJ(res, toExtPointer=TRUE)
    return(new("jobjRef", jobj=res, jclass=clsname))
}

.getFieldTypeName <- function(class, field) {
    java.type("RJavaTools")$getFieldTypeName(class, field)
}

.RsetField <- function(ref, name, value) {
    ref; name; value # force args

    obj <- ref
    if (is.null(obj)) {
        stop("cannot set a field of a NULL object")
    }

    if (!is.character(name) && length(name) != 1) {
        stop("invalid field name")
    }
    
    if(.IS_JOBJREF(obj)) {
        obj <- obj@jobj
    }
    clnam <- NULL
    o <- NULL
    if(inherits(obj, "externalptr")) {
        o <- .extPointerToJ(obj)
    } else if(is.character(obj) && length(obj) == 1) {
        clnam <- obj
    } else {
        stop("invalid object parameter")
    }
    if (is.null(o) && is.null(clnam)) {
        stop("cannot set a field of a NULL object")
    }

    value <- .toJ(value)
    if(!is.null(o)) {
        o[name] <- value
    } else {
        cls <- java.type(clnam)
        cls[name] <- value
    }
    ref
}    

.RcreateObject <-function(class, ..., silent) {
    class; silent; # force args

    if(!is.character(class) || length(class) != 1) {
        stop("RcreateObject: invalid class name")
    }

    co <- .fastr.interop.try(function() { java.type(class, silent) }, FALSE)
    if(is.null(co)) {
        return(NULL)
    }
    args <- .ellipsisToJ(co, ...)
    res <- .fastr.interop.try(function() { do.call(.fastr.interop.new, args) }, FALSE)

    # create an external pointer even for java.lang.String & co    
    .fromJ(res, toExtPointer=TRUE)
}

.RgetStringArrayCont <- function(obj) {
    obj # force args

    .getArrayCont(obj, as.character)
}

.RgetIntArrayCont <- function(obj) {
    obj # force args

    .getArrayCont(obj, as.integer)
}

.RgetBoolArrayCont <- function(obj) {
    obj # force args

    .getArrayCont(obj, as.logical)
}

.RgetCharArrayCont <- function(obj) {
    obj # force args

    # char conversion HACK
    # the regular as.integer would not work at this place, 
    # so use internal builtin to cast char to int
    .getArrayCont(obj, .fastr.interop.asVector, charToInt=TRUE)
}

.RgetShortArrayCont <- function(obj) {
    obj # force args

    .getArrayCont(obj, as.integer)
}

.RgetByteArrayCont <- function(obj) {
    obj # force args

    .getArrayCont(obj, as.raw)
}

.RgetDoubleArrayCont <- function(obj) {
    obj # force args

    .getArrayCont(obj, as.double)
}

.RgetFloatArrayCont <- function(obj) {
    obj # force args

    .getArrayCont(obj, as.double)
}

.RgetLongArrayCont <- function(obj) {
    obj # force args

    .getArrayCont(obj, as.double)
}

.RgetObjectArrayCont <- function(obj) {
    obj # force args

    if (is.null(obj)) {
        return(obj)
    }

    if(!(inherits(obj, "externalptr"))) {
        error("invalid object parameter")
    }
    obj <- attr(obj, "external.object", exact=TRUE)
    # we rely on this being called only if obj is an external array
    obj <- .fastr.interop.asVector(obj)
    lapply(obj, function(e) {.fromJ(e, toExtPointer=TRUE)})
}

.getArrayCont <- function(obj, toVectorFun, ...) {
    obj; toVectorFun # force args

    if(is.null(obj)) {
        return(obj)
    } else if(!(inherits(obj, "externalptr"))) {
        stop("invalid object parameter")
    }
    obj <- attr(obj, "external.object", exact=TRUE)
    if(is.null(obj)) {
        stop("invalid object parameter") 
    }

    if(missing(toVectorFun)) {
        res <- as.vector(obj)
    } else {
       res <- toVectorFun(obj, ...)    
    }
    res
}

.RcreateArray <- function(ar, cl) {
    ar; cl # force args

    if(is.null(ar)) {
        return(NULL)
    }

    type <- typeof(ar)
    if(type %in% c("integer", "double", "character", "logical", "raw")) {
        ar <- .vectorToJArray(ar)
        sig <- tojni(ar$getClass()$getName())
        return(new("jarrayRef", jobj=.fromJ(ar), jclass=sig, jsig=sig))
    } else if(is.list(ar)) {
        
        noJavaRef <- FALSE
        for(e in ar) {
            if(!is.null(e) && !.IS_JOBJREF(e)) {
                noJavaRef <- TRUE
                break;
            }
        }
        if(noJavaRef) {
            stop("Cannot create a Java array from a list that contains anything other than Java object references.")
        }

        if(typeof(cl)=="character" && length(cl) > 0) {
            clsName <- cl[1]
            if(is.null(java.type(clsName, T))) {
                stop(paste0("Cannot find class ", clsName, "."))
            }
            if(nchar(cl) < 253) {
                # we do not convert to jni the same as in RcreateArray
                if(startsWith(cl[1], "[")) {
                    sig <- paste0("[", cl[1])
                } else {
                    sig <- paste0("[L", cl[1], ";")
                }
            } else {
                sig <- "[Ljava/lang/Object;"
            }
        } else {
            clsName <- "java.lang.Object"
            sig <- "[Ljava/lang/Object;"
        }
    
        ar <- .fastr.interop.asJavaArray(.listToJ(ar), clsName)        
        return(new("jarrayRef", jobj=.fromJ(ar), jclass=sig, jsig=sig))
    } 
    stop("Unsupported type to create Java array from.")
}

.RtoString <- function(obj) {
    obj # force args

    if(is.null(obj)) {
        return(obj)
    }
    
    if(!inherits(obj, "externalptr")) {
        stop("RtoString: invalid object parameter")
    }
    
    if(!is.null(attr(obj, "external.object", exact=TRUE))) {
        obj <- .toJ(obj)
        if(is.polyglot.value(obj)) {
            clsName <- obj$getClass()$getName()
            if(clsName == "java.lang.Class") {
                clsName <- obj$getName()
                res <- paste0("class ", clsName)
            } else {
                res <- obj["toString"]()
            }        
        } else {
            res <- obj
        }
    } else {
        stop("RtoString: invalid object parameter")        
    } 
    res
}


.RgetStringValue <- function(obj) {
    obj # force args

    # expected to be always String or TruffleObject(String)
    if (inherits(obj, "externalptr")) {
        obj <- attr(obj, "external.object", exact=TRUE)
        if(is.polyglot.value(obj)) {
            obj$toString()
        } else {
            obj    
        }        
    } else {
        obj
    }
}

.RinitJVM <- function(...) {  
    assign(".rJava_initialized", TRUE, .env)  
}

.RJava_needs_init <- function(...) {
    !.rJava_initialized
}

.RJava_set_memprof <- function(...) {
    stop("memory profiling not enabled")
}        

.RJava_checkJVM <- function(...) {
    # do nothing
}

.RgetSimpleClassNames <- function(obj, addConditionClasses) {
    obj; addConditionClasses # force args
    if (inherits(obj, "externalptr")) {
        obj <- attr(obj, "external.object", exact=TRUE)
    }
    as.character(java.type("RJavaTools")$getSimpleClassNames(obj, as.logical(addConditionClasses)))
}

.initRJavaTools <- function(...) {
    # do nothing
}

.fromJ <- function(x, toExtPointer=FALSE) {
    x; toExtPointer # force args

    if(is.null(x)) {
        return(.jzeroRef)
    } else {        
        if(toExtPointer || is.polyglot.value(x)) {
            ep <- methods:::.newExternalptr() 
            attr(ep, "external.object") <- x            
            ep
        } else {
            x
        }    
    }
}

.ellipsisToJ <- function(...) {
    lapply(list(...), function(x) .toJ(x))
}

.listToJ <- function(l) {
    l # force args

    lapply(l, function(x) .toJ(x))
}

.toJ <- function(x) {
    x # force args

    if(is.numeric(x) || is.character(x) || is.logical(x) || is.raw(x)) {
        if(length(x) > 1) {
            return(.vectorToJArray(x))
        } else {
            if (inherits(x, "jbyte")) {
                x <- .fastr.interop.asByte(x)
            } else if (inherits(x, "jchar")) {
                x <- .fastr.interop.asChar(x)
            } else if (inherits(x, "jfloat")) {
                x <- .fastr.interop.asFloat(x)
            } else if (inherits(x, "jlong")) {
                x <- .fastr.interop.asLong(x)
            } else if (inherits(x, "jshort")) {
                x <- .fastr.interop.asShort(x)
            }
            return(x)
        }
    }

    if (is(x, "jobjRef")) {
        x <- x@jobj
    } else if (is(x, "jclassName")) {
        x <- x@jobj@jobj
    }
    if(inherits(x, "externalptr")) {
        if(.jidenticalRef(x, .jzeroRef)) {
            return(NULL)
        } else {
            xo <- attr(x, "external.object", exact=TRUE)
            if (is.null(xo)) {
                stop(paste0("missing 'external' attribute on: ", x))
            }
            if (is.polyglot.value(xo)) {
                return(xo)
            } else {                
                return(.asTruffleObject(xo, attr(x, "external.classname", exact=TRUE)))
            }
        }
    }
    x
}

.extPointerToJ <- function(x) {
    if(.jidenticalRef(x, .jzeroRef)) {
        return(NULL)
    } else {
        xo <- attr(x, "external.object", exact=TRUE)
        if (is.null(xo)) {
            stop(paste0("missing 'external' attribute on: ", x))
        }
        if (is.polyglot.value(xo)) {
            return(xo)
        } else {                
            return(.asTruffleObject(xo, attr(x, "external.classname", exact=TRUE)))
        }
    }
}

.vectorToJArray <- function(x) {
    x # force args

    switch(head(class(x), 1),
        "jbyte" = .fastr.interop.asJavaArray(x, "byte"),
        "jchar" = .fastr.interop.asJavaArray(x, "char"),
        "jfloat" = .fastr.interop.asJavaArray(x, "float"),
        "jlong" = .fastr.interop.asJavaArray(x, "long"),
        "jshort" = .fastr.interop.asJavaArray(x, "short"),
        .fastr.interop.asJavaArray(x)
    )
}

.asTruffleObject <- function(x, className=NULL) {
    x; className # force args

    if(!is.null(className)) {
        x <- switch(gsub("/", ".", className),
            "java.lang.Byte" = .fastr.interop.asByte(x),
            "java.lang.Character" = .fastr.interop.asChar(x),
            "java.lang.Float" = .fastr.interop.asFloat(x),
            "java.lang.Long" = .fastr.interop.asLong(x),
            "java.lang.Short" = .fastr.interop.asShort(x),
            x
        )        
    }
    .fastr.interop.asJavaTruffleObject(x)
}

.signatureToClassName <- function(sig) {
    sig # force args

    if(startsWith(sig, "L")) {
        if(endsWith(sig, ";")) {
            substr(sig, 2, nchar(sig) - 1)
        } else {
            substr(sig, 2, nchar(sig))
        }    
    } else {
        sig
    }
}

# could be replaced with internal builtin with a simple java cast:
# charToInt(String s) { return (int) s.charAt(0); }
.charToInt <- function(s) {
    s # force args

    as.integer(.fastr.interop.asJavaTruffleObject(s)$getBytes('US-ASCII')[1])
}

.IS_JOBJREF <- function(obj) {
    obj # force args

    inherits(obj, "jobjRef") || inherits(obj, "jarrayRef") || inherits(obj,"jrectRef")
}

.IS_JARRAYREF <- function(obj) { 
    obj # force args

    inherits(obj, "jobjRef") || inherits(obj, "jarrayRef") || inherits(obj, "jrectRef") 
}

.IS_JRECTREF <- function(obj) { 
    obj # force args

    inherits(obj,"jrectRef")
}
