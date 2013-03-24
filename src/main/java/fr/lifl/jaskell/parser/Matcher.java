package fr.lifl.jaskell.parser;

import fr.lifl.jaskell.compiler.Substitution;
import fr.lifl.jaskell.compiler.core.Alternative;
import fr.lifl.jaskell.compiler.core.Application;
import fr.lifl.jaskell.compiler.core.Constructor;
import fr.lifl.jaskell.compiler.core.ConstructorPattern;
import fr.lifl.jaskell.compiler.core.Expression;
import fr.lifl.jaskell.compiler.core.Literal;
import fr.lifl.jaskell.compiler.core.LocalBinding;
import fr.lifl.jaskell.compiler.core.Pattern;
import fr.lifl.jaskell.compiler.core.QualifiedVariable;
import fr.lifl.jaskell.compiler.core.StringLiteral;
import fr.lifl.jaskell.compiler.core.Variable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author bailly
 * @version $Id: Matcher.java 1154 2005-11-24 21:43:37Z nono $
 */
public class Matcher {

  class VarsAndCons {
    List vars;

    List cons;

    VarsAndCons(List vars, List cons) {
      this.vars = vars;
      this.cons = cons;
    }
  }

  /**
   * Constructor for Matcher.
   */
  public Matcher() {
    super();
  }

  /**
   * Take a list of variables and patterns-expressions couples and returns an
   * equivalent expression
   * 
   * @param vars
   *          a List of variables
   * @param pats
   *          a list of patterns -expression coupolse
   */
  public Expression match(List vars, List pats, Expression def) {
    /* empty case */
    if (vars.isEmpty()) {
      if (pats.isEmpty())
        return def;
      else {
        PatternMatch mt = (PatternMatch) pats.get(0);
        if (!mt.patterns.isEmpty())
          throw new ParseException(
              "Error in pattern matching : empty variable list with non empty case");
        else
          return mt.expr;
      }
    }
    if (def == null)
      def = error();
    /* partition pats according to first pattern in list */
    List sortedPats = partition(pats);
    /* try base cases */
    if (sortedPats.size() == 1) {
      /* work directly with pats */
      PatternMatch pm = (PatternMatch) pats.get(0);
      Pattern p = (Pattern) pm.patterns.get(0);
      if (p instanceof LocalBinding)
        return variableRule(vars, pats, def);
      else
        return constructorRule(vars, pats, def);
    } else {
      /* retrieve list of similar patterns */
      return mixedRule(vars, sortedPats, def);
    }
  }

  /**
   * Method mixedRule.
   * 
   * @param vars
   * @param l
   * @param def
   * @param sortedPats
   *          a List of List of PatternMatch
   * @return Expression
   */
  private Expression mixedRule(List vars, List pats, Expression def) {
    /* reverse list */
    Iterator it = pats.iterator();
    LinkedList ll = new LinkedList();
    while (it.hasNext())
      ll.addFirst(it.next());
    it = ll.iterator();
    Expression cur = def;
    while (it.hasNext()) {
      List l = (List) it.next();
      cur = match(new ArrayList(vars), l, cur);
    }
    return cur;
  }

  /**
   * Method constructorRule.
   * 
   * @param vars
   * @param pats
   * @param def
   * @return Expression
   */
  private Expression constructorRule(List vars, List pats, Expression def) {
    LocalBinding subst = (LocalBinding) vars.remove(0);
    /* construct case expression */
    Alternative alt = new Alternative();
    alt.setExpression(new Variable(subst.getName()));
    alt.putTag(((PatternMatch) pats.get(0)).expr.getTag("source")); /*
                                                                     * set
                                                                     * source
                                                                     * infor
                                                                     */
    Iterator it = pats.iterator();
    /* construct alternative elements */
    LinkedList lhm = new LinkedList();
    List ctorlist = new LinkedList(); /* list of patterns grouped by similarity */
    List constlist = null; /* list of constructors - to store subpatterns */
    List varlist = null;
    Pattern lastctor = null;
    while (it.hasNext()) {
      /* retrieve constructor pattern and constructore called */
      PatternMatch pm = (PatternMatch) it.next();
      Pattern pat0 = (Pattern) pm.patterns.remove(0);
      if (pat0 instanceof Literal) {
        if (!pat0.equals(lastctor)) {
          ctorlist.add(lastctor = pat0);
          constlist = new ArrayList();
          varlist = new LinkedList(vars);
          lhm.add(new VarsAndCons(varlist, constlist));
        }
      } else {
        ConstructorPattern cpat = ((ConstructorPattern) pat0);
        Constructor ctor = cpat.getConstructor();
        if (lastctor == null
            || !(ctor.equals(((ConstructorPattern) lastctor).getConstructor()))) {
          constlist = new LinkedList();
          varlist = new LinkedList(vars);
          /* duplicate variables list */
          lastctor = new ConstructorPattern();
          /* set source info */
          lastctor.putTag(cpat.getTag("source"));
          ctorlist.add(lastctor);
          ((ConstructorPattern) lastctor).setConstructor(cpat.getConstructor());
          Iterator itsubpat = cpat.getSubPatterns();
          List lsub = new ArrayList();
          List psub = new ArrayList();
          while (itsubpat.hasNext()) {
            Pattern pat = (Pattern) itsubpat.next();
            LocalBinding lb = LocalBinding.freshBinding();
            lsub.add(lb);
            ((ConstructorPattern) lastctor).addPattern(lb);
            psub.add(pat);
          }
          /* add newly bound variables to variable list */
          varlist.addAll(0, lsub);
          /* add subpatterns to patternlist of patternmatch */
          pm.patterns.addAll(0, psub);
          /* add couple varlist-constlist to linkedhashmap for case construction */
          lhm.add(new VarsAndCons(varlist, constlist));
        } else { /* just insert subpatterns into pm.patterns */
          Iterator itsubpat = cpat.getSubPatterns();
          List psub = new ArrayList();
          while (itsubpat.hasNext())
            psub.add(itsubpat.next());
          /* add subpatterns to patternlist of patternmatch */
          pm.patterns.addAll(0, psub);
        }
      }
      /* add current pattern match to constlist */
      constlist.add(pm);
    }
    /* create case alternatives with recursive call iterating over lhm */
    it = lhm.iterator();
    Iterator it2 = ctorlist.iterator();
    while (it.hasNext()) {
      Pattern p = (Pattern) it2.next();
      VarsAndCons vc = (VarsAndCons) it.next();
      alt.addPattern(p, match(vc.vars, vc.cons, def));
    }
    alt.setWildcard(def);
    return alt;
  }

  /**
   * Method error.
   * 
   * @return Expression
   */
  private Expression error() {
    Application app = new Application();
    QualifiedVariable qv = new QualifiedVariable("error");
    qv.addPathElement("Prelude");
    app.setFunction(qv);
    app.addArgument(new StringLiteral("no match"));
    return app;
  }

  /**
   * Method variableRule.
   * 
   * @param vars
   * @param pats
   * @param def
   * @return Expression
   */
  private Expression variableRule(List vars, List pats, Expression def) {
    LocalBinding subst = (LocalBinding) vars.remove(0);
    Iterator it = pats.iterator();
    while (it.hasNext()) {
      PatternMatch pm = (PatternMatch) it.next();
      LocalBinding var = (LocalBinding) pm.patterns.remove(0);
      /* substitue variables */
      HashMap hm = new HashMap();
      Variable v = new Variable(subst.getName());
      v.putTag(pm.expr.getTag("source")); /* set source info */
      hm.put(var.getName(), v);
      Substitution s = new Substitution(hm);
      pm.expr = (Expression) pm.expr.visit(s);
    }
    return match(vars, pats, def);
  }

  /**
   * Method partition.
   * 
   * @param pats
   * @return List
   */
  private List partition(List pats) {
    boolean varflag = false;
    List part = new ArrayList();
    List cur = null;
    Iterator it = pats.iterator();
    while (it.hasNext()) {
      PatternMatch mt = (PatternMatch) it.next();
      /* retrieve first pattern in list */
      Pattern pat = (Pattern) mt.patterns.get(0);
      if (pat instanceof LocalBinding) { /* pattern is a variable */
        if (!varflag || cur == null) {
          cur = new ArrayList();
          part.add(cur);
        }
        varflag = true;
      } else { /* constructor or litteral case */
        if (varflag || cur == null) {
          cur = new ArrayList();
          part.add(cur);
        }
        varflag = false;
      }
      /* adds match to partition */
      cur.add(mt);
    }
    return part;
  }

}